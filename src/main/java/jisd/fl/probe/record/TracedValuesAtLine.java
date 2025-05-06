package jisd.fl.probe.record;

import jisd.debug.Location;
import jisd.debug.value.ValueInfo;

import java.util.List;
import java.util.stream.Collectors;

//特定の行の変数を全て記録
//次の探索対象を探すために使用
public class TracedValuesAtLine extends TracedValueCollection {
    public TracedValuesAtLine(List<ValueInfo> valuesAtLine, Location loc) {
        this.record = valuesAtLine.stream()
                .map(vi -> convertValueInfo(vi, loc))
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }
}
