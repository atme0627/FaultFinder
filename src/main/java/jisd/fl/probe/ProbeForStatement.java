package jisd.fl.probe;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import jisd.debug.EnhancedDebugger;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.probe.info.ProbeExResult;
import jisd.fl.probe.info.ProbeResult;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.*;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.NoSuchFileException;
import java.util.*;

public class ProbeForStatement extends AbstractProbe{
    Set<SuspiciousVariable> probedValue;
    Set<String> targetClasses;

    public ProbeForStatement(FailedAssertInfo assertInfo) {
        super(assertInfo);
        probedValue = new HashSet<>();

        targetClasses = StaticAnalyzer.getClassNames();
    }

    public ProbeExResult run(int sleepTime) {
        ProbeExResult result = new ProbeExResult();
        SuspiciousVariable firstTarget = assertInfo.getVariableInfo();
        List<SuspiciousVariable> probingTargets = new ArrayList<>();
        List<SuspiciousVariable> nextTargets = new ArrayList<>();
        probingTargets.add(firstTarget);
        boolean isArgument = false;

        int depth = 0;
        while(!probingTargets.isEmpty()) {
            if(!isArgument) depth += 1;
            for (SuspiciousVariable target : probingTargets) {
                printProbeExInfoHeader(target, depth);

                Optional<SuspiciousExpression> ose = probing(sleepTime, target);
                ProbeResult pr = ProbeResult.convertSuspExpr(ose.orElseThrow(() -> new RuntimeException("Cause line is not found.")));

                if(!pr.isCausedByArgument()){
                    //原因行で他に登場した値をセット
                    TracedValueCollection valuesAtLine = traceAllValuesAtLine(pr.probeMethod(), pr.probeLine(), 0, 2000);
                    pr.setValuesInLine(valuesAtLine);
                }

                List<SuspiciousVariable> newTargets = searchNextProbeTargets(pr);
                newTargets.addAll(searchCalleeProbeTargets(pr));
                result.addElement(pr.getProbeMethodName().split("#")[0], pr.probeLine(), 0, 1);
                printProbeExInfoFooter(pr, newTargets);
                nextTargets.addAll(newTargets);
                isArgument = pr.isCausedByArgument();
            }

            probingTargets = nextTargets;
            nextTargets = new ArrayList<>();
        }
        return result;
    }

    @Override
    protected Optional<SuspiciousExpression> probing(int sleepTime, SuspiciousVariable suspVar) {
        Optional<SuspiciousExpression> result = super.probing(sleepTime, suspVar);
        int loop = 0;
        int LOOP_LIMIT = 5;
        while(result.isEmpty()) {
            loop++;
            System.err.println("[Probe For STATEMENT] Cannot get enough information.");
            System.err.println("[Probe For STATEMENT] Retry to collect information.");
            sleepTime += 2000;
            result = super.probing(sleepTime, suspVar);
            if (loop == LOOP_LIMIT) {
                System.err.println("[Probe For STATEMENT] Failed to collect information.");
                return Optional.empty();
            }
        }
        return result;
    }

    private boolean isProbed(SuspiciousVariable vi){
        for(SuspiciousVariable e : probedValue){
            if(vi.equals(e)) return true;
        }
        return false;
    }

    private void addProbedValue(SuspiciousVariable vi){
        probedValue.add(vi);
    }

    //次のprobe対象のVariableInfoを返す
    public List<SuspiciousVariable> searchNextProbeTargets(ProbeResult pr) {
        List<SuspiciousVariable> vis = new ArrayList<>();
        //感染した変数が引数のものだった場合
        if(pr.isCausedByArgument()){
            int targetArgIndex;
            MethodElement callerMethod = pr.getCallerMethod().getRight();
            if(!targetClasses.contains(callerMethod.name().getFullyQualifiedClassName())) return vis;
            try {
                MethodElement calleeMethod = new MethodElement(JavaParserUtil.getCallableDeclarationByName(pr.getCalleeMethodName()));
                targetArgIndex = calleeMethod.getIndexOfArgument(pr.getVariableInfo().getSimpleVariableName());
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }

            Expression targetArgExpr
                    = callerMethod.extractArgumentOfMethodExpr(pr.getCalleeMethodName(), pr.getCallLocationLine(), targetArgIndex);
            //TODO: 引数のExpressionとして、一旦変数のみの場合を考える
            if(!targetArgExpr.isNameExpr()) return vis;
            NameExpr argVariable = targetArgExpr.asNameExpr();
            Pair<Boolean, String> isFieldVarInfo =  isFieldVariable(argVariable.getNameAsString(), pr.getCallerMethod().getRight().fqmn());
            boolean isField = isFieldVarInfo.getLeft();
            String locateClass = (isField) ? isFieldVarInfo.getRight() : pr.getCallerMethod().getRight().fqmn();

            SuspiciousVariable vi = new SuspiciousVariable(
                    pr.getFailedTest(),
                    locateClass,
                    argVariable.getNameAsString(),
                    pr.getVariableInfo().getActualValue(),
                    pr.getVariableInfo().isPrimitive(),
                    isField,
                    pr.getVariableInfo().getArrayNth()
            );
            if(!isProbed(vi)) {
                vis.add(vi);
                addProbedValue(vi);
            }
            return vis;
        }

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

                Pair<Boolean, String> isFieldVarInfo = isFieldVariable(variableName, pr.getProbeMethodName());
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
            SuspiciousVariable probedVi = pr.getVariableInfo();
            if(probedVi.getVariableName(false, false).equals(variableName)
                    && probedVi.isField() == isField){
                continue;
            }

            //次のターゲットがprimitiveでない場合スキップ
            if(!isArray && pr.getValuesInLine().get(n).contains("instance")) continue;

            //値がNot definedの場合はスキップ
            if(pr.getValuesInLine().get(n).equals("Not defined")) continue;


            SuspiciousVariable vi;
            if(isArray) {
                vi = new SuspiciousVariable(
                        pr.getFailedTest(),
                        isField ? locateClass : pr.getProbeMethodName(),
                        pr.getValuesInLine().get(n),
                        variableName,
                        pr.getVariableInfo().isPrimitive(),
                        isField,
                        arrayNth
                );
            }
            else {
                vi = new SuspiciousVariable(
                        pr.getFailedTest(),
                        isField ? locateClass : pr.getProbeMethodName(),
                        variableName,
                        pr.getValuesInLine().get(n),
                        pr.getVariableInfo().isPrimitive(),
                        isField
                );
            }
            if(!isProbed(vi)) {
                vis.add(vi);
                addProbedValue(vi);
            }
        }
        return vis;


    }

    //probeLine中で呼び出されているメソッドに対して
    //その返り値(return)を次のprobeの対象とするためのVariableInfoを返す
    //TODO: return内のprimitive型のみ一旦考える。
    public List<SuspiciousVariable> searchCalleeProbeTargets(ProbeResult pr){
        List<SuspiciousVariable> result = new ArrayList<>();
        String main = TestUtil.getJVMMain(new CodeElementName(assertInfo.getTestMethodName()));
        String options = TestUtil.getJVMOption();
        EnhancedDebugger edbg = new EnhancedDebugger(main, options);
        //TODO: あとで直す
//        Map<String, Pair<Integer, String>> returnLineOfCalleeMethod
//                = edbg.getReturnLineOfCalleeMethod(pr.probeMethod().getFullyQualifiedClassName(), pr.probeLine(), 1);
        Map<String, Pair<Integer, String>> returnLineOfCalleeMethod = Map.of();
        for(String callee : returnLineOfCalleeMethod.keySet()) {
            CodeElementName calleeElement = new CodeElementName(callee);
            int returnLine = returnLineOfCalleeMethod.get(callee).getLeft();
            //TODO: ループ回数は考えない
            TracedValueCollection watchedValuesInReturn
                    = traceAllValuesAtLine(
                    calleeElement,
                    returnLine,
                    0,
                    2000);

            MethodElement locateMethodElement;
            try {
                locateMethodElement = MethodElement.getMethodElementByName(calleeElement);
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }
            StatementElement probeStmt = locateMethodElement.findStatementByLine(returnLine).get();

            ProbeResult calleePr = new ProbeResult(pr.getFailedTest(), pr.getVariableInfo(), probeStmt, calleeElement);
            calleePr.setValuesInLine(watchedValuesInReturn);
            result.addAll(searchNextProbeTargets(calleePr));
        }
        return result;
    }

    //probeLine中で使われている変数群を返す
    private Set<String> getNeighborVariables(ProbeResult pr){
        Set<String> neighbor = new HashSet<>();
        Set<String> watched = pr.getValuesInLine().keySet();

        Statement stmt = pr.getProbeStmt().statement();
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

    //fieldだった場合、所属するクラスを共に返す
    private Pair<Boolean, String> isFieldVariable(String variable, String targetMethod){
        String targetClass = targetMethod.split("#")[0];
        BlockStmt bs;
        NodeList<Parameter> prms;
        String fieldLocateClass = null;

        CodeElementName tmpCd = new CodeElementName(targetMethod);
        try {
            bs = JavaParserUtil.extractBodyOfMethod(tmpCd);
            prms = JavaParserUtil.getCallableDeclarationByName(tmpCd).getParameters();
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
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
                unit = JavaParserUtil.parseClass(className);
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
            ClassOrInterfaceDeclaration classDecl = unit.findFirst(ClassOrInterfaceDeclaration.class).get();
            if(classDecl.getExtendedTypes().isEmpty()) break;
            CodeElementName cd = CodeElementName.generateFromSimpleClassName(classDecl.getExtendedTypes(0).getNameAsString()).orElseThrow();
            System.out.println("parent: " + cd.getFullyQualifiedClassName());
        }

        throw new RuntimeException("Variable [" + variable + "] is not found in " + targetClass);
    }

    private void printProbeExInfoHeader(SuspiciousVariable target, int depth){
        System.out.println("============================================================================================================");
        System.out.println(" Probe For STATEMENT      DEPTH: " + depth);
        System.out.println(target.toString());
        System.out.println("============================================================================================================");
    }

    private void printProbeExInfoFooter(ProbeResult pr, List<SuspiciousVariable> nextTarget){
        printProbeStatement(pr);
        System.out.println(" [NEXT TARGET]");
        nextTarget.forEach(v -> System.out.println(v.toString()));
    }
}
