package jisd.fl.probe.record;

import com.sun.jdi.ArrayType;
import com.sun.jdi.PrimitiveType;
import com.sun.jdi.Type;
import jisd.debug.DebugResult;
import jisd.debug.Point;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.JisdInfoProcessor;
import jisd.fl.probe.assertinfo.VariableInfo;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class TracedValueRecord {
    List<TracedValue> record;

    //viで指定された変数のみ記録
    public TracedValueRecord(List<Optional<Point>> watchPoints, VariableInfo vi) {
        record = new ArrayList<>();
        extractTargetValueFromWatchPoints(watchPoints, vi);
    };

    public void addElements(List<TracedValue> data){
        record.addAll(data);
    }

    public List<TracedValue> filterByVariableName(String varName){
        List<TracedValue> result = record.stream()
                .filter(tv -> tv.variableName.equals(varName))
                .collect(Collectors.toList());

        //配列の場合[0]がついていないものも一緒に返す
        if(varName.contains("[")){
            record.stream()
                    .filter(tv -> tv.variableName.equals(varName.split("\\[")[0]))
                    .forEach(result::add);
        }
        result.sort(TracedValue::compareTo);
        return result;
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

    public void sort() {
        record.sort(TracedValue::compareTo);
    }

    //variableInfoで指定された変数の値を抽出する
    public void extractTargetValueFromWatchPoints(List<Optional<Point>> watchPoints, VariableInfo vi){
        //get Values from debugResult
        //実行されなかった行の情報は飛ばす。
        //実行されたがnullのものは含む。
        for (Optional<Point> op : watchPoints) {
            Point p;
            if (op.isEmpty()) continue;
            p = op.get();
            Optional<DebugResult> odr = p.getResults(vi.getVariableName());
            if(odr.isEmpty()) continue;
            this.addElements(convertDebugResult(odr.get(), vi));
        }

        if (this.record.isEmpty()) {
            throw new RuntimeException("there is not target value in watch point.");
        }
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

    //TODO: 必要ないかも
    public void clear(){
        record = null;
    }
}