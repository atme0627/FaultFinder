package jisd.fl.probe.record;

import jisd.debug.DebugResult;
import jisd.debug.Point;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.assertinfo.VariableInfo;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class TracedValueRecord {
    private final List<TracedValue> record;

    //viで指定された変数のみ記録
    public TracedValueRecord(List<Optional<Point>> watchPoints, VariableInfo vi) {
        record = extractTargetValueFromWatchPoints(watchPoints, vi);
    };

    public List<TracedValue> filterByVariableName(String varName){
        return record.stream()
                .filter(tv -> tv.variableName.equals(varName))
                .sorted(TracedValue::compareTo)
                .collect(Collectors.toList());
    }

    //TODO: 返り値をなんとかする
    public Map<String, String> filterByCreateAt(LocalDateTime createAt){
        List<TracedValue> result =  record.stream()
                .filter(tv -> tv.createAt.equals(createAt))
                .collect(Collectors.toList());
        return result.stream().collect(Collectors.toMap(tv -> tv.variableName,tv ->  tv.createAt.toString()));
    }

    public boolean isEmpty(){
        return record.isEmpty();
    }

    //variableInfoで指定された変数の値を抽出する
    private List<TracedValue> extractTargetValueFromWatchPoints(List<Optional<Point>> watchPoints, VariableInfo vi){
        List<TracedValue> result = watchPoints.stream()
                //WatchPointからDebugResultを得る
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(wp -> wp.getResults(vi.getVariableName()))
                //DebugResultからList<TracedValue>に変換
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(dr -> convertDebugResult(dr, vi))
                //List<TracedValue>を一つにまとめる
                .flatMap(Collection::stream)
                .sorted(TracedValue::compareTo)
                .collect(Collectors.toList());

        if (result.isEmpty()) {
            throw new RuntimeException("there is not target value in watch point.");
        }

        return result;
    }


    //TODO: primitive型のラッパークラスを考慮
    private List<TracedValue> convertDebugResult(DebugResult dr, VariableInfo vi){
        List<TracedValue> result = new ArrayList<>();
        List<ValueInfo> vis = dr.getValues();

        //変数が配列の場合
        if(vi.isArray()){
            for(ValueInfo v : vis) {
                List<ValueInfo> children = v.expand();
                //配列は定義されているが要素がない場合、スキップ
                if(children.isEmpty()) continue;
                ValueInfo targetChild = children.get(vi.getArrayNth());
                result.add(new TracedValue(
                        targetChild.getCreatedAt(),
                        dr.getLocation(),
                        vi.getVariableName(false, true),
                        targetChild.getValue()
                ));
            }
            return result;
        }

        //変数がprimitive型の場合
        if(vi.isPrimitive()){
            for(ValueInfo v : vis) {
                result.add(new TracedValue(
                        v.getCreatedAt(),
                        dr.getLocation(),
                        vi.getVariableName(),
                        v.getValue()
                ));
            }
            return result;
        }

        //変数が参照型の場合
        for(ValueInfo v : vis) {
            result.add(new TracedValue(
                    v.getCreatedAt(),
                    dr.getLocation(),
                    vi.getVariableName(),
                    v.getValue()
            ));
        }
        return result;
    }

    public void print(String varName){
        for(TracedValue tv : filterByVariableName(varName)) {
            System.out.println("    >> " + tv);
        }
    }

    public void printAll(){
        for(TracedValue tv : record){
            System.out.println("    >> " + tv);
        }
    }
}