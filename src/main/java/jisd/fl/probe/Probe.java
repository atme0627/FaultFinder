package jisd.fl.probe;

import jisd.fl.coverage.CoverageAnalyzer;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.sbfl.SbflStatus;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.StaticAnalyzer;
import jisd.info.ClassInfo;
import jisd.info.LocalInfo;
import jisd.info.MethodInfo;

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

        List<Integer> assignLines = StaticAnalyzer.getAssignLine(
                variableInfo.getLocateClass(),
                variableInfo.getVariableName(true));

        printWatchedValues(watchedValues, variableInfo, assignLines);
        ProbeResult result = searchProbeLine(watchedValues, assignLines);
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
             siblingMethods = getSiblingMethods(callerClass, result.getCallerMethod());
        }
        catch (IOException | InterruptedException e){
            throw new RuntimeException(e);
        }
        result.setSiblingMethods(siblingMethods);
        enableStdOut();
        return result;
    }

    @Override
    protected ProbeResult searchProbeLine(List<ProbeInfo> watchedValues, List<Integer> assignedLine){
        System.out.println("    >> Probe Info: Searching probe line.");


        boolean isFound = false;
        ProbeResult result = new ProbeResult();

        List<ProbeInfo> matchValues = new ArrayList<>();
        for (ProbeInfo pi : watchedValues) {
            if (assertInfo.eval(pi.value)) {
                matchValues.add(pi);
                isFound = true;
            }
        }
        if (!isFound) throw new RuntimeException("No matching rows found.");


        //assignLineのうち実行されたものを集める
        List<Integer> executedAssignedLines = new ArrayList<>();
        for(int l : assignedLine){
            for(ProbeInfo pi : matchValues){
                if(pi.loc.getLineNumber() == l) {
                    executedAssignedLines.add(l);
                    break;
                }
            }
        }

        //新しいものから探して最初にexecutedAssignedLines内の値に一致したものがobjective
        int probeLine = 0;
        String locationClass = null;
        if(executedAssignedLines.isEmpty()) {
            probeLine = matchValues.get(0).loc.getLineNumber() - 1;
            locationClass = matchValues.get(0).loc.getClassName();
        }
        else {
            matchValues.sort(Comparator.reverseOrder());
            for (ProbeInfo pi : matchValues) {
                boolean isFounded = false;
                for(int l : executedAssignedLines) {
                    if (pi.loc.getLineNumber() == l) {
                        probeLine = pi.loc.getLineNumber();
                        locationClass = pi.loc.getClassName();
                        isFounded = true;
                        break;
                    }
                }
                if(isFounded) break;
            }
        }

        //実行しているメソッドを取得
        String probeMethod = StaticAnalyzer.getMethodNameFormLine(locationClass ,probeLine);
        //シグニチャも含める
        result.setProbeLine(probeLine);
        result.setProbeMethod(probeMethod);
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
        return StaticAnalyzer.getMethodNameFormLine(callerClass, line);
    }

    Set<String> getSiblingMethods(String callerClass, String callerMethod) throws IOException, InterruptedException {
        CoverageAnalyzer analyzer = new CoverageAnalyzer();
        Set<String> siblingMethods = new HashSet<>();
        String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");
        Set<String> targetMethods = StaticAnalyzer.getMethodNames(targetSrcDir, callerClass, false);
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
