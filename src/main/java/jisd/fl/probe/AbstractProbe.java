package jisd.fl.probe;

import com.sun.jdi.VMDisconnectedException;
import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.debug.Location;
import jisd.debug.Point;
import jisd.debug.value.PrimitiveInfo;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.StaticAnalyzer;
import jisd.fl.util.TestUtil;
import jisd.info.ClassInfo;
import jisd.info.StaticInfoFactory;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.*;

public abstract class AbstractProbe {

    FailedAssertInfo assertInfo;
    Debugger dbg;
    StaticInfoFactory targetSif;
    StaticInfoFactory testSif;
    PrintStream stdOut = System.out;
    PrintStream stdErr = System.err;


    public AbstractProbe(FailedAssertInfo assertInfo) {
        String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");
        String targetBinDir = PropertyLoader.getProperty("d4jTargetBinDir");
        String testSrcDir = PropertyLoader.getProperty("d4jTestSrcDir");
        String testBinDir = PropertyLoader.getProperty("d4jTestBinDir");

        this.assertInfo = assertInfo;
        this.dbg = createDebugger();

        this.targetSif = new StaticInfoFactory(targetSrcDir, targetBinDir);
        this.testSif = new StaticInfoFactory(testSrcDir, testBinDir);
    }

    public abstract List<Integer> getCanSetLine(VariableInfo variableInfo);
    public abstract ProbeResult run(int sleepTime) throws IOException;

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
        Pair<Integer, Integer> probeLines = StaticAnalyzer.getRangeOfStatement(locationClass).get(probeLine);

        //シグニチャも含める
        result.setLines(probeLines);
        result.setProbeMethod(probeMethod);
        result.setSrc(getProbeStatement(locationClass, probeLines));
        return result;
    }

    //Statementのソースコードを取得
    private String getProbeStatement(String locationClass, Pair<Integer, Integer> probeLines){
        ClassInfo ci = targetSif.createClass(locationClass);
        String[] src = ci.src().split("\n");
        StringBuilder stmt = new StringBuilder();
        for(int i = probeLines.getLeft(); i <= probeLines.getRight(); i++) {
            stmt.append(src[i - 1]);
            stmt.append("\n");
        }
        return stmt.toString();
    }

    //一回のprobeを行う
    //条件を満たす行の情報を返す
    protected ProbeResult probing(int sleepTime, VariableInfo variableInfo){
        List<ProbeInfo> watchedValues = extractInfoFromDebugger(variableInfo, sleepTime);
        List<Integer> assignLines = StaticAnalyzer.getAssignLine(
                variableInfo.getLocateClass(),
                variableInfo.getVariableName(true));
        printWatchedValues(watchedValues, variableInfo, assignLines);
        ProbeResult result = searchProbeLine(watchedValues, assignLines);
        printProbeStatement(result, variableInfo);
        return result;
    }

    //primitive型の値のみを取得
    //variableInfoが参照型の場合、fieldを取得してその中から目的のprimitive型の値を探す
    private List<ProbeInfo> getValuesFromDebugResult(DebugResult dr, VariableInfo variableInfo){
        List<ProbeInfo> pis = new ArrayList<>();
        List<ValueInfo> vis;
        try {
            vis = dr.getValues();
        }
        catch (RuntimeException e) {
            return pis;
        }

        for(ValueInfo vi : vis) {
            //対象の変数がnullの場合飛ばす
            if (vi.getValue().isEmpty()) continue;

            PrimitiveInfo pi = getPrimitiveInfoFromValueInfo(vi, variableInfo);
            LocalDateTime createdAt = vi.getCreatedAt();
            Location loc = dr.getLocation();
            String value = Objects.requireNonNull(pi).getValue();
            pis.add(new ProbeInfo(createdAt, loc, value));
        }
        return pis;
    }

    //参照型の配列には未対応
    private PrimitiveInfo getPrimitiveInfoFromValueInfo(ValueInfo vi, VariableInfo variableInfo){
        //プリミティブ型の場合
        if(variableInfo.isPrimitive()) return (PrimitiveInfo) vi;

        //プリミティブ型の配列の場合
        if(variableInfo.isArray()){
            int arrayNth = variableInfo.getArrayNth();
            ArrayList<ValueInfo> arrayElements = vi.ch();
            return (PrimitiveInfo) arrayElements.get(arrayNth);
        }

        //参照型の場合
        if(variableInfo.isPrimitive()){
            ArrayList<ValueInfo> fieldElements = vi.ch();
            boolean isFound = false;
            String fieldName = variableInfo.getTargetField().getVariableName();
            for(ValueInfo e : fieldElements){
                if(e.getName().equals(fieldName)){
                    getPrimitiveInfoFromValueInfo(e, variableInfo.getTargetField());
                    isFound = true;
                    break;
                }
            }
            if(!isFound) throw new NoSuchElementException(fieldName + " is not found in fields of" + variableInfo.getVariableName(false));
        }
        return null;
    }

    protected List<ProbeInfo> extractInfoFromDebugger(VariableInfo variableInfo, int sleepTime){
        disableStdOut("    >> Probe Info: Running debugger.");
        List<Integer> canSetLines = getCanSetLine(variableInfo);
        disableStdOut(Arrays.toString(canSetLines.toArray()));
        String dbgMain = variableInfo.getLocateClass();
        String varName = variableInfo.getVariableName(true);
        List<Optional<Point>> watchPoints = new ArrayList<>();
        //set watchPoint
        dbg.setMain(dbgMain);
        String[] fieldNames = {varName};
        for (int line : canSetLines) {
            watchPoints.add(dbg.watch(line, fieldNames));
        }

        //run debugger
        try {
            dbg.run(sleepTime);
        } catch (VMDisconnectedException ignored) {
        }
        dbg.exit();

        enableStdOut();
        List<ProbeInfo> watchedValues = getInfoFromWatchPoints(watchPoints, variableInfo);
        watchedValues.sort(ProbeInfo::compareTo);
        return watchedValues;
    }

    private List<ProbeInfo> getInfoFromWatchPoints(List<Optional<Point>> watchPoints, VariableInfo variableInfo){
        //get Values from debugResult
        String varName = variableInfo.getVariableName(true);
        List<ProbeInfo> watchedValues = new ArrayList<>();
        for (Optional<Point> op : watchPoints) {
            Point p;
            if (op.isEmpty()) continue;
            p = op.get();
            Optional<DebugResult> od = p.getResults(varName);
            if (od.isEmpty()) continue;
            watchedValues.addAll(getValuesFromDebugResult(od.get(), variableInfo));
        }

        if (watchedValues.isEmpty()) {
            throw new RuntimeException("Probe#run\n" +
                    "there is not target value in watch point.");
        }
        return watchedValues;
    }

    protected void disableStdOut(String msg){
        System.setOut(stdOut);
        System.out.println(msg);
        PrintStream nop = new PrintStream(new OutputStream() {
            public void write(int b) { /* noop */ }
        });
        System.setOut(nop);
        System.setErr(nop);
    }

    protected void enableStdOut(){
        System.setOut(stdOut);
        System.setErr(stdErr);
    }

    protected void printWatchedValues(List<ProbeInfo> watchedValues, VariableInfo variableInfo, List<Integer> assignLine){
        System.out.println("    >> ---------------------------------");
        System.out.println("    >> [field name] "
                + variableInfo.getVariableName(true)
                + " [type] "
                + variableInfo.getVariableType());
        System.out.println("    >> [assigned line] " + Arrays.toString(assignLine.toArray()));
        for(ProbeInfo values : watchedValues){
            System.out.println("    >>" + values);
        }
    }

    protected void printProbeStatement(ProbeResult result, VariableInfo variableInfo){
        System.out.println("    >> [probe Statement]");
        Pair<Integer, Integer> probeLines = result.getProbeLines();
        String[] src = result.getSrc().split("\n");
        int l = 0;
        for(int i = probeLines.getLeft(); i <= probeLines.getRight(); i++) {
            System.out.println("    >> " + i + ": " + src[l++]);
        }
        System.out.println("    >> ---------------------------------");
    }

    protected Debugger createDebugger(){
        return TestUtil.testDebuggerFactory(assertInfo.getTestMethodName());
    }

    protected static class ProbeInfo implements Comparable<Probe.ProbeInfo>{
        LocalDateTime createAt;
        Location loc;
        String value;

        ProbeInfo(LocalDateTime createAt,
                    Location loc,
                  String value){
            this.createAt = createAt;
            this.loc = loc;
            this.value = value;
        }

        @Override
        public int compareTo(ProbeInfo o) {
            return createAt.compareTo(o.createAt);
        }

        @Override
        public String toString(){
            return "CreateAt: " + createAt + " Line: " + loc.getLineNumber() + " value: " + value;
        }
    }
}
