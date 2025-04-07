package jisd.fl.probe;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.util.JavaParserUtil;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.analyze.CodeElement;
import jisd.fl.util.analyze.StaticAnalyzer;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.NoSuchFileException;
import java.util.*;

//値のStringを比較して一致かどうかを判定
//理想的には、"==" と同じ方法で判定したいが、型の問題で難しそう
public class ProbeEx extends AbstractProbe {
    Set<VariableInfo> probedValue;
    Set<String> targetClasses;

    public ProbeEx(FailedAssertInfo assertInfo) {
        super(assertInfo);
        probedValue = new HashSet<>();
        String targetSrcDir = PropertyLoader.getProperty("targetSrcDir");
        targetClasses = StaticAnalyzer.getClassNames(targetSrcDir);
    }

    public ProbeExResult run(int sleepTime) {
        ProbeExResult result = new ProbeExResult();
        int depth = 0;
        VariableInfo firstTarget = assertInfo.getVariableInfo();
        List<VariableInfo> probingTargets = new ArrayList<>();
        List<VariableInfo> nextTargets = new ArrayList<>();
        probingTargets.add(firstTarget);
        boolean isArgument = false;

        //初めのprobe対象変数がローカル変数の場合、変数が所属するmethodをdepth1でマーキングメソッドに含める。
        if(!firstTarget.isField()){
            System.out.println("    >> ============================================================================================================");
            System.out.println("    >> Probe Ex     DEPTH: 1");
            System.out.println("    >> [MARKING METHODS]");
            System.out.println("    >> " + firstTarget.getLocateMethod(true));

            result.addElement(firstTarget.getLocateMethod(true), 1, 1);
        }

        while(!probingTargets.isEmpty()) {
            if(!isArgument) depth += 1;
            if(depth > 10) break;
            for (VariableInfo target : probingTargets) {
                if(isProbed(target))continue;
                addProbedValue(target);

                printProbeExInfoHeader(target, depth);
                ProbeResult pr = probing(sleepTime, target);
                if(pr == null) continue;
                if(pr.isArgument()){
                    //感染した変数が引数のものだった場合
                    Pair<Integer, String> caller = getCallerMethod(pr.getWatchedAt(), target);
                    int probeLine = caller.getLeft();
                    Pair<Integer, Integer> probeLines = Pair.of(probeLine, probeLine);
                    pr.setCallerMethod(caller);
                    pr.setLines(probeLines);
                    pr.setSrc(getProbeStatement(caller.getRight().split("#")[0], probeLines));
                }

                List<VariableInfo> newTargets = searchNextProbeTargets(pr);
                List<String> markingMethods = searchMarkingMethods(pr, assertInfo.getTestMethodName());
                result.addAll(markingMethods, depth);
                printProbeExInfoFooter(pr, newTargets, markingMethods);

                nextTargets.addAll(newTargets);
                isArgument = pr.isArgument();
            }

            probingTargets = nextTargets;
            nextTargets = new ArrayList<>();
        }
        return result;
    }
    //probeLine内で呼び出されたメソッド群を返す
    public List<String> searchMarkingMethods(ProbeResult pr, String testMethod){
        List<String> markingMethods = new ArrayList<>();
        //引数が感染していた場合、呼び出しメソッドがマーキング対象
        if(pr.isArgument()){
            Pair<Integer, String> caller = pr.getCallerMethod();
            if(caller != null) {
                markingMethods.add(caller.getRight());
            }
            return markingMethods;
        }

        //probe lineが所属するmethodをmarking methodに追加
        if(targetClasses.contains(pr.getProbeMethod().split("#")[0])) {
            markingMethods.add(pr.getProbeMethod());
        }

        Pair<Integer, Integer> lines = pr.getProbeLines();
        Set<String> calledMethods = getCalleeMethods(testMethod, pr.getProbeMethod(), lines);
        for(String called : calledMethods){
            if(targetClasses.contains(called.split("#")[0])) markingMethods.add(called);
        }
        return markingMethods;
    }

    //次のprobe対象のVariableInfoを返す
    public List<VariableInfo> searchNextProbeTargets(ProbeResult pr) {
        List<VariableInfo> vis = new ArrayList<>();
        //感染した変数が引数のものだった場合
        if(pr.isArgument()){
            if(pr.getCallerMethod() == null) return vis;
            if(!targetClasses.contains(pr.getCallerMethod().getRight().split("#")[0])) return vis;
            String argVariable = getArgumentVariable(pr);
            if(argVariable == null) return vis;
            //argVariableが純粋な変数でない（関数呼び出しなどを行っている）場合、probeは行わない
            if(!argVariable.matches("[A-Za-z][A-Za-z0-9]*") || argVariable.equals("null")) return vis;

            Pair<Boolean, String> isFieldVarInfo =  isFieldVariable(argVariable, pr.getCallerMethod().getRight());
            boolean isField = isFieldVarInfo.getLeft();
            String locateClass = (isField) ? isFieldVarInfo.getRight() : pr.getCallerMethod().getRight();

            VariableInfo vi = new VariableInfo(
                    locateClass,
                    argVariable,
                    pr.getVariableInfo().isPrimitive(),
                    isField,
                    pr.getVariableInfo().isArray(),
                    pr.getVariableInfo().getArrayNth(),
                    pr.getVariableInfo().getActualValue(),
                    pr.getVariableInfo().getTargetField()
                    );
            vis.add(vi);
        }
        else {
            Set<String> neighborVariables = getNeighborVariables(pr);
            for(String n : neighborVariables){
                String variableName = n;
                boolean isArray;
                boolean isField = false;
                int arrayNth;
                String locateClass = "";
                //フィールドかどうか判定
                if(n.contains("this.")){
                    isField = true;
                    variableName = n.substring("this.".length());
                    //thisなしのものが観測されている場合はスキップ
                    if(neighborVariables.contains(variableName)) continue;

                    Pair<Boolean, String> isFieldVarInfo = isFieldVariable(variableName, pr.getProbeMethod());
                    locateClass = isFieldVarInfo.getRight();
                }
                //配列かどうか判定
                if(n.contains("[")){
                    variableName = variableName.split("\\[")[0];
                    arrayNth = Integer.parseInt(n.substring(n.indexOf("[") + 1, n.indexOf("]")));
                    isArray = true;
                    //配列のindexが3以上のものはスキップ
                    if(arrayNth >= 3) continue;
                }
                else {
                    arrayNth = -1;
                    isArray = false;
                }

                //元のprobe対象と同じ変数の場合スキップ
                VariableInfo probedVi = pr.getVariableInfo();
                if(probedVi.getVariableName(false, false).equals(variableName)
                    && probedVi.isField() == isField){
                    continue;
                }

                //次のターゲットがprimitiveでない場合スキップ
                if(!isArray && pr.getValuesInLine().get(n).contains("instance")) continue;

                //値がNot definedの場合はスキップ
                if(pr.getValuesInLine().get(n).equals("Not defined")) continue;

                VariableInfo vi = new VariableInfo(
                        isField ? locateClass : pr.getProbeMethod(),
                        variableName,
                        pr.getVariableInfo().isPrimitive(),
                        isField,
                        isArray,
                        arrayNth,
                        pr.getValuesInLine().get(n),
                        null
                );
                vis.add(vi);
            }
        }
        return vis;
    }

    private boolean isProbed(VariableInfo vi){
        for(VariableInfo e : probedValue){
            if(vi.equals(e)) return true;
        }
        return false;
    }

    private void addProbedValue(VariableInfo vi){
        probedValue.add(vi);
    }

    //fieldだった場合、所属するクラスを共に返す
    private Pair<Boolean, String> isFieldVariable(String variable, String targetMethod){
        String targetClass = targetMethod.split("#")[0];
        BlockStmt bs;
        List<Parameter> prms;
        String fieldLocateClass = null;


        try {
            MethodDeclaration md = JavaParserUtil.parseMethod(targetMethod);
            bs = md.getBody().get();
            prms = md.getParameters();
        } catch (NoSuchFileException e) {
            //メソッドがコンストラクタの場合
            try{
                ConstructorDeclaration cd = JavaParserUtil.parseConstructor(targetMethod);
                bs = cd.getBody();
                prms = cd.getParameters();
            }
            catch (NoSuchFileException ex){
                throw new RuntimeException(e);

            }
        }

        //method内で定義されたローカル変数の場合
        List<VariableDeclarator> vds = bs.findAll(VariableDeclarator.class);
        for(VariableDeclarator vd : vds){
            if(vd.getName().toString().equals(variable)){
                return Pair.of(false, null);
            }
        }

        //methodの引数由来の場合
        for(Parameter prm : prms){
            if(prm.getName().toString().equals(variable)){
                return Pair.of(false, null);
            }
        }

        //fieldの場合
        //親クラス内を再帰的に調べる
        //インターフェースの場合は考えない
        String className = targetClass;
        while(true) {
            CompilationUnit unit;
            try {
                unit = JavaParserUtil.parseClass(className, false);
            } catch (NoSuchFileException e) {
                break;
            }

            List<FieldDeclaration> fds = unit.findAll(FieldDeclaration.class);
            vds = new ArrayList<>();
            for (FieldDeclaration fd : fds) {
                vds.addAll(fd.getVariables());
            }

            for (VariableDeclarator vd : vds) {
                if (vd.getName().toString().equals(variable)) {
                    return Pair.of(true, targetClass);
                }
            }

            //親クラスを探す
            String targetSrcDir = PropertyLoader.getProperty("targetSrcDir");
            ClassOrInterfaceDeclaration classDecl = unit.findFirst(ClassOrInterfaceDeclaration.class).get();
            if(classDecl.getExtendedTypes().isEmpty()) break;
            className = StaticAnalyzer.getClassNameWithPackage(targetSrcDir, classDecl.getExtendedTypes(0).getNameAsString());
            System.out.println("parent: " + className);
        }

        throw new RuntimeException("Variable \"" + variable + "\" is not found in " + targetClass);
    }

    //probeLine中で使われている変数群を返す
    private Set<String> getNeighborVariables(ProbeResult pr){
        Set<String> neighbor = new HashSet<>();
        Set<String> watched = pr.getValuesInLine().keySet();

        //srcがstatementとしてパースできない場合は空の集合を返す
        Statement stmt = null;
        try {
            stmt = StaticJavaParser.parseStatement(pr.getSrc());
        }
        catch (ParseProblemException e){
            return neighbor;
        }

        List<SimpleName> variableNames = stmt.findAll(SimpleName.class);
        variableNames.forEach((v) -> {
            for(String w : watched) {
                //プリミティブ型の場合
                if (w.equals(v.toString()) || w.equals("this." + v)) {
                    neighbor.add(w);
                }

                if(!w.contains("[")) continue;
                String withoutArray = w.substring(0, w.indexOf("["));
                //配列の場合
                if (withoutArray.equals(v.toString()) || withoutArray.equals("this." + v)) {
                    neighbor.add(w);
                }
            }
        });
        return neighbor;
    }

    private void printProbeExInfoHeader(VariableInfo variableInfo, int depth){
        System.out.println("    >> ============================================================================================================");
        System.out.println("    >> Probe Ex     DEPTH: " + depth
                + "    [PROBE TARGET] "
                + variableInfo.getVariableName(true, true)
                + "   [ACTUAL] "
                + variableInfo.getActualValue());
        System.out.println(
                "                                [CLASS] "
                + variableInfo.getLocateClass());
        System.out.println("    >> ============================================================================================================");
    }

    private void printProbeExInfoFooter(ProbeResult pr, List<VariableInfo> nextTarget, List<String> markingMethods){
        printProbeStatement(pr);
        System.out.println("    >> [MARKING METHODS]");
        for(String m : markingMethods){
            System.out.println("    >> " + m);
        }
        System.out.println("    >> [NEXT TARGET]");
        for(VariableInfo vi : nextTarget){
            System.out.println("    >> [VARIABLE] " + vi.getVariableName(true, true) + "    [ACTUAL] " + vi.getActualValue());
        }
    }

    //メソッド呼び出しで使われた変数名を返す
    protected String getArgumentVariable(ProbeResult pr) {
        Pair<Integer, String> callerNameAndCallLocation = pr.getCallerMethod();
        int index = 0;
        try {
            index = getIndexOfArgument(pr);
        }
        catch (RuntimeException e){
            System.out.println(e);
            System.out.println("    >> Probe Info:     " + pr.getVariableInfo().getVariableName() + " is probably declared in field.");
            return null;
        }
        int line = callerNameAndCallLocation.getLeft();
        String locateMethod = callerNameAndCallLocation.getRight();

//        Pair<Integer, Integer> lines = rangeOfStatements.getOrDefault(line, Pair.of(line, line));
        CodeElement tmpCd = new CodeElement(locateMethod);
        Range tmpRange = StaticAnalyzer.getRangeOfStatement(tmpCd, line).orElse(null);
        Pair<Integer, Integer> lines = tmpRange != null ? Pair.of(tmpRange.begin.line, tmpRange.end.line) : Pair.of(line, line);
        String calledMethod = pr.getProbeMethod();
        calledMethod = calledMethod.substring(calledMethod.indexOf("#")+1, calledMethod.indexOf("("));

        int finalIndex = index;
        String finalCalledMethod = calledMethod;
        class BlockStmtVisitor extends GenericVisitorAdapter<String, Integer> {
            @Override
            public String visit(final MethodCallExpr n, final Integer line) {
                if (!(n.getEnd().get().line < lines.getLeft() || lines.getRight() < n.getBegin().get().line)) {
                    if (n.getNameAsString().equals(finalCalledMethod)) {
                        if (n.getArguments().size() > finalIndex) {
                            Expression expr = n.getArgument(finalIndex);
                            if (expr.isCastExpr()) {
                                expr = expr.asCastExpr().getExpression();
                            }

                            if (expr.isNameExpr()) return expr.asNameExpr().getNameAsString();
                            return null;
                        }
                    }
                }
                return super.visit(n, line);
            }

            @Override
            public String visit(final ObjectCreationExpr n, final Integer line) {
                if (!(n.getEnd().get().line < lines.getLeft() || lines.getRight() < n.getBegin().get().line)) {
                    if (n.getType().getName().toString().equals(finalCalledMethod)){
                        if (n.getArguments().size() > finalIndex) {
                            Expression expr = n.getArgument(finalIndex);
                            if(expr.isCastExpr()) {
                                expr = expr.asCastExpr().getExpression();
                            }

                            if(expr.isNameExpr()) return expr.asNameExpr().getNameAsString();
                            return null;
                        }
                    }
                }
                return super.visit(n, line);
            }

            @Override
            public String visit(final ExplicitConstructorInvocationStmt n, final Integer line) {
                if (!(n.getEnd().get().line < lines.getLeft() || lines.getRight() < n.getBegin().get().line)) {
                    if(n.getArguments().size() > finalIndex) {
                        return n.getArgument(finalIndex).toString();
                    }
                }
                return super.visit(n, line);
            }
        }

        BlockStmt bs;
        try {
            MethodDeclaration md = JavaParserUtil.parseMethod(locateMethod);
            bs = md.getBody().get();
        } catch (NoSuchFileException e) {
            //targetMethodがコンストラクタの場合
            try {
                ConstructorDeclaration cd = JavaParserUtil.parseConstructor(locateMethod);
                bs = cd.getBody();
            } catch (NoSuchFileException ex) {
                System.err.println(ex);
                return null;
            }
        }

        return bs.accept(new BlockStmtVisitor(), line);
    }

    private int getIndexOfArgument(ProbeResult pr){
        String targetMethod = pr.getProbeMethod();
        String variable = pr.getVariableInfo().getVariableName();
        int index = -1;
        NodeList<Parameter> prms;
        try {
            MethodDeclaration md = JavaParserUtil.parseMethod(targetMethod);
            prms = md.getParameters();
        } catch (NoSuchFileException e) {
            //targetMethodがコンストラクタの場合
            try {
                ConstructorDeclaration cd = JavaParserUtil.parseConstructor(targetMethod);
                prms = cd.getParameters();
            } catch (NoSuchFileException ex) {
                throw new RuntimeException(ex);
            }
        }

        for(int i = 0; i < prms.size(); i++){
            Parameter prm = prms.get(i);
            if(prm.getName().toString().equals(variable)){
                index = i;
            }
        }

        if(index == -1) throw new RuntimeException("parameter " + variable + " is not found.");
        return index;
    }
}
