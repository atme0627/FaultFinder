package experiment.util;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.sun.jdi.InvalidStackFrameException;
import com.sun.jdi.VMDisconnectedException;
import jisd.debug.*;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.ProbeResult;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.probe.record.TracedValue;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.probe.record.TracedValuesAtLine;
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.*;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.stream.Collectors;

public class SuspiciousVariableFinder {
    private final TestMethodElement targetTestCase;
    private final CodeElementName targetTestCaseName;

    public SuspiciousVariableFinder(CodeElementName targetTestCaseName) throws NoSuchFileException {
        this.targetTestCaseName = targetTestCaseName;
        this.targetTestCase = TestMethodElement.getTestMethodElementByName(targetTestCaseName);
    }

    public List<VariableInfo> find(){
        List<VariableInfo> result = new ArrayList<>();
        List<Expression> assertActualExpr = targetTestCase.findAssertActualExpr();

        for(Expression actualExpr : assertActualExpr){
            int assertLine = actualExpr.getRange().get().begin.line;
            TracedValueCollection allWatchedValues = traceAllValuesAtLine(assertLine);
            result.addAll(searchProbeTargets(allWatchedValues, actualExpr));
        }
        return result;
    }


    //line行目で、全ての変数が取っている値を記録する
    private TracedValueCollection traceAllValuesAtLine(int line){
        String main = TestUtil.getJVMMain(targetTestCaseName);
        String options = TestUtil.getJVMOption();
        Debugger dbg = new Debugger(main, options);

        dbg.setMain(targetTestCaseName.getFullyQualifiedClassName());
        Optional<Point> watchPointAtLine = dbg.watch(line);

        //run Test debugger
        try {
            dbg.run(3000);
        } catch (VMDisconnectedException | InvalidStackFrameException e) {
            System.err.println(e);
        }

        //この行で値が観測されることが保証されている
        List<DebugResult> drs = new ArrayList<>(watchPointAtLine.get().getResults().values());
        Location loc = drs.get(0).getLocation();
        //行のnthLoop番目のvalueInfoを取得
        List<ValueInfo> valuesAtLine = drs.stream()
                .map(DebugResult::getValues)
                .map(vis -> vis.get(0))
                .collect(Collectors.toList());

        TracedValueCollection watchedValues = new TracedValuesAtLine(valuesAtLine, loc);
        dbg.exit();
        dbg.clearResults();
        return watchedValues;
    }

    //次のprobe対象のVariableInfoを返す
    private List<VariableInfo> searchProbeTargets(TracedValueCollection watchedValuesInLine, Expression actualExpr) {
        List<VariableInfo> result = new ArrayList<>();

        Set<String> neighborVariables = getNeighborVariables(watchedValuesInLine, actualExpr);
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

                Pair<Boolean, String> isFieldVarInfo = isFieldVariable(variableName, targetTestCase.fqmn());
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

            String nextTargetActualValue = watchedValuesInLine.filterByVariableName(n).get(0).value;

            //次のターゲットがprimitiveでない場合スキップ
            if(!isArray && nextTargetActualValue.contains("instance")) continue;

            //値がNot definedの場合はスキップ
            if(nextTargetActualValue.equals("Not defined")) continue;

            VariableInfo vi = new VariableInfo(
                    isField ? locateClass : targetTestCaseName.getFullyQualifiedMethodName(),
                    variableName,
                    true,
                    isField,
                    isArray,
                    arrayNth,
                    nextTargetActualValue,
                    null
            );
            result.add(vi);
        }
        return result;
    }

    //probeLine中で呼び出されているメソッドに対して
    //その返り値(return)を次のprobeの対象とするためのVariableInfoを返す
    //TODO: return内のprimitive型のみ一旦考える。
    private List<VariableInfo> searchCalleeProbeTargets(ProbeResult pr) {
        List<VariableInfo> result = new ArrayList<>();
        String main = TestUtil.getJVMMain(targetTestCaseName);
        String options = TestUtil.getJVMOption();
        EnhancedDebugger edbg = new EnhancedDebugger(main, options);
        Map<String, Integer> returnLineOfCalleeMethod
                = edbg.getReturnLineOfCalleeMethod(pr.probeMethod().getFullyQualifiedClassName(), pr.probeLine());

        for (String callee : returnLineOfCalleeMethod.keySet()) {
            CodeElementName calleeElement = new CodeElementName(callee);
            int returnLine = returnLineOfCalleeMethod.get(callee);
            //TODO: ループ回数は考えない
            TracedValueCollection watchedValuesInReturn
                    = traceAllValuesAtLine(returnLine);

            MethodElement locateMethodElement;
            try {
                locateMethodElement = MethodElement.getMethodElementByName(calleeElement);
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }
            Expression returnExpr =
                    locateMethodElement.findStatementByLine(returnLine).get()
                    .statement().asReturnStmt().getExpression().get();
            result.addAll(searchProbeTargets(watchedValuesInReturn, returnExpr));
        }
        return result;
    }


    //Actual中で使われている変数群を返す
    private Set<String> getNeighborVariables(TracedValueCollection watchedValuesInLine, Expression actualExpr){
        Set<String> neighbor = new HashSet<>();
        List<SimpleName> variableNames = actualExpr.findAll(SimpleName.class);
        variableNames.forEach((v) -> {
            for(TracedValue tv : watchedValuesInLine.getAll()) {
                String name = tv.variableName;
                //プリミティブ型の場合
                if (name.equals(v.toString()) || name.equals("this." + v)) {
                    neighbor.add(name);
                }

                if(!name.contains("[")) continue;
                String withoutArray = name.substring(0, name.indexOf("["));
                //配列の場合
                if (withoutArray.equals(v.toString()) || withoutArray.equals("this." + v)) {
                    neighbor.add(name);
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
}
