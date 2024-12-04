package jisd.fl.probe;

import com.sun.jdi.VMDisconnectedException;
import jisd.debug.DebugResult;
import jisd.debug.Location;
import jisd.debug.Point;
import jisd.debug.value.ValueInfo;
import jisd.fl.coverage.CoverageAnalyzer;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.sbfl.SbflStatus;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.StaticAnalyzer;
import jisd.fl.util.TestUtil;
import jisd.info.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.*;

public class Probe extends AbstractProbe{
    PrintStream stdOut = System.out;
    PrintStream stdErr = System.err;

    public Probe(FailedAssertInfo assertInfo){
        super(assertInfo);
        String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");
        String targetBinDir = PropertyLoader.getProperty("d4jTargetBinDir");
        this.sif = new StaticInfoFactory(targetSrcDir, targetBinDir);
    }

    public Map<String, ArrayList<Integer>> getCanSetLine() {
        ClassInfo ci = sif.createClass(assertInfo.getTypeName());
        FieldInfo fi = ci.field(assertInfo.getFieldName());
        return fi.canSet();
    }

    //assertInfoで指定されたtypeのクラスの中で
    //失敗テスト実行時に、actualに一致した瞬間に呼び出しているメソッドを返す。
    @Override
    public ProbeResult run(int sleepTime) {
        Map<String, ArrayList<Integer>> canSetLines = getCanSetLine();
        List<Optional<Point>> watchPoints = new ArrayList<>();
        List<ProbeInfo> watchedValues = new ArrayList<>();
        ProbeResult result = new ProbeResult();

        disableStdOut("    >> Probe Info: Setting watch points.");
        //set watchPoint
        dbg.setMain(assertInfo.getTypeName());
        String[] fieldName = {"this." + assertInfo.getFieldName()};
        for(List<Integer> lineWithVar : canSetLines.values()) {
            for (int line : lineWithVar) {
                watchPoints.add(dbg.watch(line, fieldName));
            }
        }

        disableStdOut("    >> Probe Info: Running debugger.");
        //run debugger
        try {
            dbg.run(sleepTime);
        } catch (VMDisconnectedException ignored) {
        }
        dbg.exit();

        disableStdOut("    >> Probe Info: Extracting values from debug results.");
        //get Values from debugResult
        for (Optional<Point> op : watchPoints) {
            Point p;
            if (op.isEmpty()) continue;

            p = op.get();
            Optional<DebugResult> od = p.getResults("this." +  assertInfo.getFieldName());
            if(od.isEmpty()) continue;

            ProbeInfo values = getValuesFromDebugResult(od.get());
            if(values == null) continue;

            watchedValues.add(values);
        }

        if(watchedValues.isEmpty()) {
            throw new RuntimeException("Probe#run\n" +
                                        "there is not target value in watch point.");
        }

        //debugResultを通過した順にソート
        watchedValues.sort(ProbeInfo::compareTo);
        enableStdOut();
        printWatchedValues(watchedValues);
        disableStdOut("    >> Probe Info: Searching probe line.");
        //初めてactualの値と一致した場所をprobeの対象とする。
        //一致した時点で終了
        int probeLine = 0;
        boolean isFound = false;
        for(ProbeInfo values : watchedValues){
            Location loc = values.loc;
            String value = values.value;
            if (!assertInfo.eval(value)) continue;
            //実行しているメソッドを取得
            probeLine = loc.getLineNumber();
            int finalProbeLine = probeLine;
            final String[] probeMethod = new String[1];
            canSetLines.forEach((method, list)->{
                if(list.contains(finalProbeLine)){
                    probeMethod[0] = method;
                }
            });
            isFound = true;
            //シグニチャも含める
            result.setProbeMethod(loc.getClassName() + "#" + probeMethod[0]);
            break;
        }
        if (!isFound) throw new RuntimeException("No matching rows found.");

        //メソッドを呼び出したメソッドをコールスタックから取得
        disableStdOut("    >> Probe Info: Searching caller method from call stack.");
        String callerMethod = getCallerMethod(probeLine);
        result.setCallerMethod(callerMethod);

        disableStdOut("    >> Probe Info: Searching sibling method from coverage.");
        //callerメソッドが呼び出したメソッドをカバレッジから取得
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

    private ProbeInfo getValuesFromDebugResult(DebugResult dr){
        ValueInfo vi;
        try {
            vi = dr.getLatestValue();
        }
        catch (RuntimeException e) {
            return null;
        }

        LocalDateTime createdAt = vi.getCreatedAt();
        Location loc = dr.getLocation();
        String value;

        //配列の場合
        if(assertInfo.isArray()){
            ArrayList<ValueInfo> array = vi.ch();
            //null check
            if(array.isEmpty()) return null;
            value = array.get(assertInfo.getArrayNth()).getValue();
        }
        else {
            value = vi.getValue();
        }

        return new ProbeInfo(createdAt, loc, value);
    }

    String getCallerMethod(int probeLine) {
        PrintStream stdout = System.out;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos);
        dbg = TestUtil.testDebuggerFactory(assertInfo.getTestClassName(), assertInfo.getTestMethodName());
        dbg.setMain(assertInfo.getTypeName());
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
        CoverageCollection covOfFailedTest = analyzer.analyze(assertInfo.getTestClassName(), assertInfo.getTestClassName() + "#" + assertInfo.getTestMethodName());
        Map<String, SbflStatus> covOfCallerClass = covOfFailedTest.getCoverageOfTarget(callerClass, Granularity.METHOD);
        covOfCallerClass.forEach((method, status) -> {
            if(status.isElementExecuted()){
                siblingMethods.add(method);
            }
        });
        return siblingMethods;
    }

    private void disableStdOut(String msg){
        System.setOut(stdOut);
        System.out.println(msg);
        PrintStream nop = new PrintStream(new OutputStream() {
            public void write(int b) { /* noop */ }
        });
        System.setOut(nop);
        System.setErr(nop);
    }

    private void enableStdOut(){
        System.setOut(stdOut);
        System.setErr(stdErr);
    }
}
