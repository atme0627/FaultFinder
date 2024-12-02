package jisd.fl.probe;

import com.sun.jdi.VMDisconnectedException;
import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.debug.Location;
import jisd.debug.Point;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.util.PropertyLoader;
import jisd.info.*;
import org.apache.commons.lang3.tuple.Triple;

import java.time.LocalDateTime;
import java.util.*;

public class Probe extends AbstractProbe{
    public Probe(Debugger dbg, FailedAssertInfo assertInfo){
        super(dbg, assertInfo);
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

        dbg.setMain(assertInfo.getTypeName());

        //set watchPoint
        String[] fieldName = {"this." + assertInfo.getFieldName()};
        for(List<Integer> lineWithVar : canSetLines.values()) {
            for (int line : lineWithVar) {
                watchPoints.add(dbg.watch(line, fieldName));
            }
        }

        //run debugger
        try {
            dbg.run(sleepTime);
        } catch (VMDisconnectedException ignored) {
        }
        dbg.exit();

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
        printWatchedValues(watchedValues);

        //初めてactualの値と一致した場所をprobeの対象とする。
        //一致した時点で終了
        boolean isFound = false;
        for(ProbeInfo values : watchedValues){
            Location loc = values.loc;
            String value = values.value;
            if (!assertInfo.eval(value)) continue;
            //実行しているメソッドを取得
            int probeLine = loc.getLineNumber();
            final String[] probeMethod = new String[1];
            canSetLines.forEach((method, list)->{
                if(list.contains(probeLine)){
                    probeMethod[0] = method;
                }
            });
            isFound = true;
            result.setProbeMethod(loc.getClassName() + "#" + probeMethod[0]);
            break;
        }
        if (!isFound) throw new RuntimeException("No matching rows found.");


        //メソッドを呼び出したメソッドをコールスタックから取得

        //calleeメソッドが呼び出したメソッドをカバレッジから取得

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


    private void printWatchedValues(List<ProbeInfo> watchedValues){
        for(ProbeInfo values : watchedValues){
            LocalDateTime createAt = values.createAt;
            Location loc = values.loc;
            String value = values.value;
            System.out.println("CreateAt: " + createAt + " Line: " + loc.getLineNumber() + " value: " + value);
        }
    }

    private static class ProbeInfo implements Comparable<ProbeInfo>{
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
    }
}
