package jisd.fl.probe.record;

import jisd.debug.Location;
import jisd.debug.value.ValueInfo;
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

    @Override
    protected List<TracedValue> convertArrayInfo(ValueInfo vi, Location loc){
        List<ValueInfo> children = vi.expand();
        int targetIndex = target.getArrayNth();
        return List.of(
                    new TracedValue(
                    children.get(targetIndex).getCreatedAt(),
                    vi.getName() + "[" + targetIndex + "]",
                    children.get(targetIndex).getValue(),
                            loc.getLineNumber()
                    ));
    }
}
