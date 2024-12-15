package jisd.fl.probe;

import jisd.fl.coverage.CoverageAnalyzer;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.sbfl.SbflStatus;
import jisd.fl.util.StaticAnalyzer;
import jisd.info.ClassInfo;
import jisd.info.LocalInfo;
import jisd.info.MethodInfo;
import org.apache.commons.lang3.tuple.Pair;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

public class Probe extends AbstractProbe{

    public Probe(FailedAssertInfo assertInfo){
        super(assertInfo);
    }

    //assertInfoで指定されたtypeのクラスの中で
    //失敗テスト実行時に、actualに一致した瞬間に呼び出しているメソッドを返す。
    @Override
    public ProbeResult run(int sleepTime) {
        //targetのfieldを直接probe
        VariableInfo variableInfo = assertInfo.getVariableInfo().getTargetField();
        ProbeResult result = probing(sleepTime, variableInfo);

        //メソッドを呼び出したメソッドをコールスタックから取得
        disableStdOut("    >> Probe Info: Searching caller method from call stack.");
        Pair<Integer, Integer> probeLines = result.getProbeLines();
        String callerMethod = getCallerMethod(probeLines, variableInfo);
        result.setCallerMethod(callerMethod);

        //callerメソッドが呼び出したメソッドをカバレッジから取得
        disableStdOut("    >> Probe Info: Searching sibling method from coverage.");
        String callerClass = callerMethod.split("#")[0];
        Set<String> siblingMethods;
        try {
             siblingMethods = getSiblingMethods(callerClass, result.getCallerMethod());
        }
        catch (IOException | InterruptedException e){
            throw new RuntimeException(e);
        }
        result.setSiblingMethods(siblingMethods);
        enableStdOut();
        return result;
    }

    String getCallerMethod(Pair<Integer, Integer> probeLines, VariableInfo variableInfo) {
        PrintStream stdout = System.out;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos);
        dbg = createDebugger();
        dbg.setMain(variableInfo.getLocateClass());
        dbg.stopAt(probeLines.getLeft());
        disableStdOut("    >> Probe Info: Running debugger.");
        dbg.run(2000);
        System.setOut(ps);
        dbg.where();
        System.setOut(stdout);

        //callerMethodをシグニチャ付きで取得する
        String[] stackTrace = bos.toString().split("\\n");
        String callerMethod = stackTrace[2];
        StringBuilder callerClassBuilder = new StringBuilder(callerMethod);
        callerClassBuilder.setCharAt(callerClassBuilder.lastIndexOf("."), '#');
        String callerClass = callerClassBuilder.substring(callerClassBuilder.indexOf("]") + 2).split("#")[0];
        int line = Integer.parseInt(callerMethod.substring(callerMethod.indexOf("(") + 1, callerMethod.length() - 1).substring(6));
        return StaticAnalyzer.getMethodNameFormLine(callerClass, line);
    }

    Set<String> getSiblingMethods(String callerClass, String callerMethod) throws IOException, InterruptedException {
        CoverageAnalyzer analyzer = new CoverageAnalyzer();
        Set<String> siblingMethods = new HashSet<>();
        Set<String> targetMethods = StaticAnalyzer.getMethodNames(callerClass, false);
        disableStdOut("    >> Probe Info: Analyzing coverage.");

        Set<String> canBeCallMethods = StaticAnalyzer.getCalledMethodsForMethod(callerMethod, targetMethods);
        System.out.println(Arrays.toString(canBeCallMethods.toArray()));

        CoverageCollection covOfFailedTest = analyzer.analyze(assertInfo.getTestClassName(), assertInfo.getTestMethodName());
        Map<String, SbflStatus> covOfCallerClass = covOfFailedTest.getCoverageOfTarget(callerClass, Granularity.METHOD);
        covOfCallerClass.forEach((method, status) -> {
            if(canBeCallMethods.contains(method) && status.isElementExecuted()){
                siblingMethods.add(method);
            }
        });
        return siblingMethods;
    }

    @Override
    public List<Integer> getCanSetLine(VariableInfo variableInfo) {
        List<Integer> canSetLines = new ArrayList<>();
        ClassInfo ci = targetSif.createClass(variableInfo.getLocateClass());

        if(variableInfo.isField()) {
            Map<String, ArrayList<Integer>> canSet = ci.field(variableInfo.getVariableName()).canSet();
            for (List<Integer> lineWithVar : canSet.values()) {
                canSetLines.addAll(lineWithVar);
            }
            return canSetLines;
        }
        else {
            MethodInfo mi = ci.method(variableInfo.getLocateMethod());
            LocalInfo li = mi.local(variableInfo.getVariableName());

            return li.canSet();
        }
    }
}
