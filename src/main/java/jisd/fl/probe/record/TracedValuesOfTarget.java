package jisd.fl.probe.record;

import jisd.fl.core.entity.susp.SuspiciousVariable;

import java.util.List;

//viで指定された変数のみ記録
//原因行特定のために使用
public class TracedValuesOfTarget extends TracedValueCollection {
    final SuspiciousVariable target;

    private TracedValuesOfTarget(List<TracedValue> record, SuspiciousVariable target){
        super(record);
        this.target = target;
    }
    public static TracedValueCollection of(List<TracedValue> record, SuspiciousVariable target){
        return new TracedValuesOfTarget(record, target);
    }

}
