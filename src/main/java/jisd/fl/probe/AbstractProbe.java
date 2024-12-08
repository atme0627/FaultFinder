package jisd.fl.probe;

import com.sun.jdi.VMDisconnectedException;
import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.debug.Location;
import jisd.debug.Point;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestUtil;
import jisd.info.ClassInfo;
import jisd.info.FieldInfo;
import jisd.info.StaticInfoFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractProbe {

    FailedAssertInfo assertInfo;
    Debugger dbg;
    StaticInfoFactory sif;
    ClassInfo ci;
    PrintStream stdOut = System.out;
    PrintStream stdErr = System.err;
    Map<String, ArrayList<Integer>> canSetLines;

    public AbstractProbe(FailedAssertInfo assertInfo) {
        String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");
        String targetBinDir = PropertyLoader.getProperty("d4jTargetBinDir");

        this.assertInfo = assertInfo;
        this.dbg = createDebugger();

        this.sif = new StaticInfoFactory(targetSrcDir, targetBinDir);
        this.ci = sif.createClass(assertInfo.getTypeName());
        this.canSetLines =  getCanSetLine();
    }

    public Map<String, ArrayList<Integer>> getCanSetLine() {
        FieldInfo fi = ci.field(assertInfo.getFieldName());
        return fi.canSet();
    }

    public abstract ProbeResult run(int sleepTime) throws IOException;
    abstract ProbeResult searchProbeLine(List<ProbeInfo> watchedValues);

    protected void printWatchedValues(List<ProbeInfo> watchedValues){
        System.out.println("    >> ---------------------------------");
        System.out.println("    >> field name: "
                + assertInfo.getVariableName()
                + "."
                + assertInfo.getFieldName()
                + " type: " + assertInfo.getTypeName());

        for(ProbeInfo values : watchedValues){
            System.out.println("    >> Probe Info: " + values);
        }
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

    List<ProbeInfo> extractInfoFromDebugger(String dbgMain,
                                            String fieldName,
                                            int sleepTime){

        disableStdOut("    >> Probe Info: Running debugger.");
        List<Optional<Point>> watchPoints = new ArrayList<>();
        //set watchPoint
        dbg.setMain(dbgMain);
        String[] fieldNames = {"this." + fieldName};
        for (List<Integer> lineWithVar : canSetLines.values()) {
            for (int line : lineWithVar) {
                watchPoints.add(dbg.watch(line, fieldNames));
            }
        }

        //run debugger
        try {
            dbg.run(sleepTime);
        } catch (VMDisconnectedException ignored) {
        }
        dbg.exit();

        enableStdOut();
        List<ProbeInfo> watchedValues = getInfoFromWatchPoints(watchPoints);
        watchedValues.sort(ProbeInfo::compareTo);
        return watchedValues;
    }

    private  List<ProbeInfo> getInfoFromWatchPoints(List<Optional<Point>> watchPoints){
        //get Values from debugResult
        List<ProbeInfo> watchedValues = new ArrayList<>();
        for (Optional<Point> op : watchPoints) {
            Point p;
            if (op.isEmpty()) continue;
            p = op.get();
            Optional<DebugResult> od = p.getResults("this." + assertInfo.getFieldName());
            if (od.isEmpty()) continue;
            ProbeInfo values = getValuesFromDebugResult(od.get());
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

    void printProbeLine(int probeLine){
        System.out.println("    >> [probe line]");
        System.out.println("    >> " + probeLine + ": " + ci.src().split("\n")[probeLine - 1]);
        System.out.println("    >> ---------------------------------");
    }

    Debugger createDebugger(){
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
