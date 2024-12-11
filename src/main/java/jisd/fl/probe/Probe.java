package jisd.fl.probe;

import jisd.debug.Location;
import jisd.fl.coverage.CoverageAnalyzer;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.sbfl.SbflStatus;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.StaticAnalyzer;
import jisd.info.ClassInfo;

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

        List<ProbeInfo> watchedValues = extractInfoFromDebugger(variableInfo, sleepTime);
        printWatchedValues(watchedValues, variableInfo);
        ProbeResult result = searchProbeLine(watchedValues);

        int probeLine = result.getProbeLine();
        printProbeLine(probeLine, variableInfo);

        //メソッドを呼び出したメソッドをコールスタックから取得
        disableStdOut("    >> Probe Info: Searching caller method from call stack.");
        String callerMethod = getCallerMethod(probeLine, variableInfo);
        result.setCallerMethod(callerMethod);

        //callerメソッドが呼び出したメソッドをカバレッジから取得
        disableStdOut("    >> Probe Info: Searching sibling method from coverage.");
        String callerClass = callerMethod.split("#")[0];
        Set<String> siblingMethods;
        try {
             siblingMethods = getSiblingMethods(callerClass);
        }
        catch (IOException | InterruptedException e){
            throw new RuntimeException(e);
        }
        result.setSiblingMethods(siblingMethods);
        enableStdOut();
        return result;
    }

    @Override
    protected ProbeResult searchProbeLine(List<ProbeInfo> watchedValues){
        System.out.println("    >> Probe Info: Searching probe line.");

        String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");

        //初めてactualの値と一致した場所をprobeの対象とする。
        //一致した時点で終了
        int probeLine = 0;
        boolean isFound = false;
        ProbeResult result = new ProbeResult();
        for (ProbeInfo values : watchedValues) {
            Location loc = values.loc;
            String value = values.value;
            if (!assertInfo.eval(value)) continue;
            //実行しているメソッドを取得
            probeLine = loc.getLineNumber();
            String probeMethod = StaticAnalyzer.getMethodNameFormLine(targetSrcDir, loc.getClassName() ,probeLine);
            isFound = true;
            //シグニチャも含める
            result.setProbeLine(probeLine);
            result.setProbeMethod(probeMethod);
            break;
        }
        if (!isFound) throw new RuntimeException("No matching rows found.");
        return result;
    }

    String getCallerMethod(int probeLine, VariableInfo variableInfo) {
        PrintStream stdout = System.out;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos);
        dbg = createDebugger();
        dbg.setMain(variableInfo.getLocateClass());
        dbg.stopAt(probeLine);
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
        String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");
        return StaticAnalyzer.getMethodNameFormLine(targetSrcDir, callerClass, line);
    }

    Set<String> getSiblingMethods(String callerClass) throws IOException, InterruptedException {
        CoverageAnalyzer analyzer = new CoverageAnalyzer();
        Set<String> siblingMethods = new HashSet<>();
        disableStdOut("    >> Probe Info: Analyzing coverage.");
        CoverageCollection covOfFailedTest = analyzer.analyze(assertInfo.getTestClassName(), assertInfo.getTestMethodName());
        Map<String, SbflStatus> covOfCallerClass = covOfFailedTest.getCoverageOfTarget(callerClass, Granularity.METHOD);
        covOfCallerClass.forEach((method, status) -> {
            if(status.isElementExecuted()){
                siblingMethods.add(method);
            }
        });
        return siblingMethods;
    }

    @Override
    public List<Integer> getCanSetLine(VariableInfo variableInfo) {
        List<Integer> canSetLines = new ArrayList<>();
        ClassInfo ci = targetSif.createClass(variableInfo.getLocateClass());
        Map<String, ArrayList<Integer>> canSet= ci.field(variableInfo.getVariableName()).canSet();
        for (List<Integer> lineWithVar : canSet.values()) {
            canSetLines.addAll(lineWithVar);
        }
        return canSetLines;
    }
}
