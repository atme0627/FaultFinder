package jisd.fl.probe.record;

import java.util.List;

//特定の行の変数を全て記録
//次の探索対象を探すために使用
public class TracedValuesAtLine extends TracedValueCollection {
    private TracedValuesAtLine(List<TracedValue> record){
        super(record);
    }
    public static TracedValueCollection of(List<TracedValue> record){
        return new TracedValuesAtLine(record);
    }
}
