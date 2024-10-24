package jisd.fl.probe;

import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.debug.Point;
import jisd.debug.value.ValueInfo;
import jisd.info.ClassInfo;
import jisd.info.LocalInfo;
import jisd.info.MethodInfo;
import jisd.info.StaticInfoFactory;

import java.util.*;

//値のStringを比較して一致かどうかを判定
//理想的には、"==" と同じ方法で判定したいが、型の問題で難しそう
public class Probe {
    Debugger dbg;
    StaticInfoFactory sif;
    FailedAssertInfo assertInfo;

    public Probe(Debugger dbg, FailedAssertInfo assertInfo){
        this.dbg = dbg;
        this.assertInfo = assertInfo;
        this.sif = new StaticInfoFactory(assertInfo.getSrcDir(), assertInfo.getBinDir());
    }

    //呼び出し関係は考えていないバージョン
    //そのメソッド内だけ検索する
    public ArrayList<Integer> getLineWithVar(){
        ClassInfo ci = sif.createClass(assertInfo.getTestClassName());
        MethodInfo mi = ci.method(assertInfo.getTestMethodName());
        LocalInfo li = mi.local(assertInfo.getVariableName());
        return li.canSet();
    }

    //assert文から遡って、最後に変数が目的の条件を満たしている行を返す。
    public int run(){
        ArrayList<Integer> lineWithVar = getLineWithVar();
        ArrayList<Optional<Point>> watchPoints = new ArrayList<>();
        ArrayList<Optional<DebugResult>> results = new ArrayList<>();
        String[] varName = {assertInfo.getVariableName()};
        //--debug
        ArrayList<HashMap<String, DebugResult>> drs = new ArrayList<>();

        dbg.setMain(assertInfo.getTestClassName());
        //run program
        for(int line : lineWithVar){
            watchPoints.add(dbg.watch(line, varName));
        }

        dbg.run(1000);

        dbg.exit();

        //get debugResult
        for(Optional<Point> op : watchPoints){
            Point p;
            if(op.isPresent()){
                p = op.get();
            }
            else {
                throw new NoSuchElementException("There is no information in a watchPoint.");
            }
            results.add(p.getResults(assertInfo.getVariableName()));
        }

        //probe
        Collections.reverse(results);
        for(Optional<DebugResult> odr : results){
            DebugResult dr = null;
            if(odr.isPresent()){
                dr = odr.get();
            }
            else {
                throw new NoSuchElementException("There is no information in a DebugResult.");
            }

            ValueInfo vi = dr.lv();
            if(assertInfo.eval(vi.getValue())){
                return dr.getLocation().getLineNumber();
            }
        }
        throw new RuntimeException("No matching rows found.");
    }
}
