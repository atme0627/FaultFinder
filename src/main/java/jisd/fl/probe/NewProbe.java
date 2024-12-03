package jisd.fl.probe;

import com.sun.jdi.VMDisconnectedException;
import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.debug.Point;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.util.PropertyLoader;
import jisd.info.ClassInfo;
import jisd.info.LocalInfo;
import jisd.info.MethodInfo;
import jisd.info.StaticInfoFactory;

import java.util.*;

//値のStringを比較して一致かどうかを判定
//理想的には、"==" と同じ方法で判定したいが、型の問題で難しそう
public class NewProbe extends AbstractProbe{

    public NewProbe(FailedAssertInfo assertInfo) {
        super(assertInfo);
        String testSrcDir = PropertyLoader.getProperty("testSrcDir");
        String testBinDir = PropertyLoader.getProperty("testBinDir");
        this.sif = new StaticInfoFactory(testSrcDir, testBinDir);
    }

    //呼び出し関係は考えていないバージョン
    //そのメソッド内だけ検索する
    public List<Integer> getLineWithVar() {
        ClassInfo ci = sif.createClass(assertInfo.getTestClassName());
        MethodInfo mi = ci.method(assertInfo.getTestMethodName());
        LocalInfo li = mi.local(assertInfo.getVariableName());
        return li.canSet();
    }

    //assert文から遡って、最後に変数が目的の条件を満たしている行で呼び出しているメソッドを返す。
    public ProbeResult run(int sleepTime) {
        List<Integer> lineWithVar = getLineWithVar();
        ArrayList<Optional<Point>> watchPoints = new ArrayList<>();
        ArrayList<Optional<DebugResult>> results = new ArrayList<>();
        String[] varName = {assertInfo.getVariableName()};

        dbg.setMain(assertInfo.getTestClassName());
        //run program
        for (int line : lineWithVar) {
            watchPoints.add(dbg.watch(line, varName));
        }

        try {
            dbg.run(sleepTime);
        } catch (VMDisconnectedException ignored) {
        }

        dbg.exit();

        //get debugResult
        for (Optional<Point> op : watchPoints) {
            Point p;
            if (op.isPresent()) {
                p = op.get();
            } else {
                throw new NoSuchElementException("There is no information in a watchPoint.");
            }
            results.add(p.getResults(assertInfo.getVariableName()));
        }

        //probe
        Collections.reverse(results);
        int probeLine = -1;
        for (Optional<DebugResult> odr : results) {
            DebugResult dr = null;
            if (odr.isPresent()) {
                dr = odr.get();
            } else {
                throw new NoSuchElementException("There is no information in a DebugResult.");
            }

            //値がセットされていない場合はスキップ
            ValueInfo vi = null;
            try {
                vi = dr.lv();
            } catch (RuntimeException e) {
                continue;
            }

            if (assertInfo.eval(vi.getValue())) {
                probeLine = dr.getLocation().getLineNumber();
                //System.out.println("probeLine: " + probeLine);
            }
        }

        if (probeLine == -1) {
            throw new RuntimeException("No matching rows found.");
        } else {
            //probeLineにいるときにはまだその行は実行されていない
            //return probeLineParser(probeLine - 1);
            return null;
        }
    }

}
