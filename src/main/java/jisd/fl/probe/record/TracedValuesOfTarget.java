package jisd.fl.probe.record;

import jisd.debug.Location;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.info.SuspiciousVariable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

//viで指定された変数のみ記録
//原因行特定のために使用
public class TracedValuesOfTarget extends TracedValueCollection {
    final SuspiciousVariable target;

    public TracedValuesOfTarget(SuspiciousVariable target, List<ValueInfo> valuesOfTarget, Map<LocalDateTime, Location> locationAtTime) {
        this.target = target;
        this.record = convertValuesOfTarget(valuesOfTarget, locationAtTime);
    }

    private List<TracedValue> convertValuesOfTarget(List<ValueInfo> valuesOfTarget, Map<LocalDateTime, Location> locationAtTime){
        return  valuesOfTarget.stream()
                        .map(v -> convertValueInfo(v, locationAtTime.get(v.getCreatedAt())))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
    }

    @Override
    protected List<TracedValue> convertArrayInfo(ValueInfo vi, Location loc){
        List<ValueInfo> children = vi.expand();
        int targetIndex = target.getArrayNth();
        return List.of(
                    new TracedValue(
                    children.get(targetIndex).getCreatedAt(),
                    loc,
                    vi.getName() + "[" + targetIndex + "]",
                    children.get(targetIndex).getValue()
            ));
    }
}
