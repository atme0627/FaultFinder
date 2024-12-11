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
import jisd.fl.util.TestUtil;
import jisd.info.ClassInfo;
import jisd.info.StaticInfoFactory;

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
    protected abstract ProbeResult searchProbeLine(List<ProbeInfo> watchedValues);

    //primitive型の値のみを取得
    //variableInfoが参照型の場合、fieldを取得してその中から目的のprimitive型の値を探す
    private ProbeInfo getValuesFromDebugResult(DebugResult dr, VariableInfo variableInfo){
        ValueInfo vi;
        try {
            vi = dr.getLatestValue();
        }
        catch (RuntimeException e) {
            return null;
        }

        //対象の変数がnullの場合、nullを返す
        if(vi.getValue().isEmpty()) return null;

        PrimitiveInfo pi = getPrimitiveInfoFromValueInfo(vi, variableInfo);
        LocalDateTime createdAt = vi.getCreatedAt();
        Location loc = dr.getLocation();
        String value = Objects.requireNonNull(pi).getValue();

        return new ProbeInfo(createdAt, loc, value);
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
            ProbeInfo values = getValuesFromDebugResult(od.get(), variableInfo);
            if (values == null) continue;
            watchedValues.add(values);
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

    protected void printWatchedValues(List<ProbeInfo> watchedValues, VariableInfo variableInfo){
        System.out.println("    >> ---------------------------------");
        System.out.println("    >> field name: "
                + variableInfo.getVariableName(true)
                + " type: "
                + variableInfo.getVariableType());

        for(ProbeInfo values : watchedValues){
            System.out.println("    >> Probe Info: " + values);
        }
    }

    protected void printProbeLine(int probeLine, VariableInfo variableInfo){
        ClassInfo ci = targetSif.createClass(variableInfo.getLocateClass());
        System.out.println("    >> [probe line]");
        System.out.println("    >> " + probeLine + ": " + ci.src().split("\n")[probeLine - 1]);
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
