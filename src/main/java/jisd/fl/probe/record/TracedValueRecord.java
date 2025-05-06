package jisd.fl.probe.record;

import com.sun.jdi.*;
import jisd.debug.DebugResult;
import jisd.debug.Location;
import jisd.debug.Point;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.assertinfo.VariableInfo;

import javax.lang.model.type.ReferenceType;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

//valueInfoを受け取って、TracedValueに変換する責務を持つ
public class TracedValueRecord {
    private final List<TracedValue> record;

    //特定の行の変数を全て記録
    //次の探索対象を探すために使用
    public TracedValueRecord(List<ValueInfo> valuesAtLine, Location loc){
        record = valuesAtLine.stream()
                .map(vi -> convertValueInfo(vi, loc))
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    //viで指定された変数のみ記録
    //原因行特定のために使用
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
                .map(dr -> convertDebugResultOfTarget(dr, vi))
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
    private List<TracedValue> convertDebugResultOfTarget(DebugResult dr, VariableInfo vi){
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


    private List<TracedValue> convertValueInfo(ValueInfo vi, Location loc){
        Type typeOfValue = vi.getJValue().type();
        //変数が配列の場合
        if(typeOfValue instanceof ArrayType){
            return convertArrayInfo(vi, loc);
        }

        //変数がプリミティブ型の場合
        if(typeOfValue instanceof PrimitiveType){
            return convertPrimitiveInfo(vi, loc);
        }

        //変数がプリミティブ型のラッパークラスの場合
        if(isPrimitiveWrapper(typeOfValue)){
            return convertPrimitiveWrapperInfo(vi, loc);
        }

        //変数が参照型の場合
        return convertReferenceInfo(vi, loc);

    }

    private List<TracedValue> convertArrayInfo(ValueInfo vi, Location loc){
        List<ValueInfo> children = vi.expand();
        List<TracedValue> result = new ArrayList<>();
        for(int i = 0; i < children.size(); i++){
            result.add(new TracedValue(
                    children.get(i).getCreatedAt(),
                    loc,
                    children.get(i).getName() + "[" + i + "]",
                    children.get(i).getValue()
            ));
        }
        return result;
    }

    private List<TracedValue> convertPrimitiveInfo(ValueInfo vi, Location loc){
        return List.of(new TracedValue(
                vi.getCreatedAt(),
                loc,
                vi.getName(),
                vi.getValue()
        ));
    }

    private List<TracedValue> convertPrimitiveWrapperInfo(ValueInfo vi, Location loc){
        ValueInfo primitiveValue = vi.getField("value");
        return convertPrimitiveInfo(primitiveValue, loc);
    }

    //暫定
    private List<TracedValue> convertReferenceInfo(ValueInfo vi, Location loc){
        return List.of(new TracedValue(
                vi.getCreatedAt(),
                loc,
                vi.getName(),
                vi.getValue()
        ));
    }

    private boolean isPrimitiveWrapper(Type type) {
        //プリミティブ型のラッパークラスの名前
        final Set<String> WRAPPER_CLASS_NAMES = new HashSet<>(Arrays.asList(
                Boolean.class.getName(),
                Byte.class.getName(),
                Character.class.getName(),
                Short.class.getName(),
                Integer.class.getName(),
                Long.class.getName(),
                Float.class.getName(),
                Double.class.getName(),
                Void.class.getName()
        ));

        if (type instanceof ClassType) {
            return WRAPPER_CLASS_NAMES.contains(type.name());
        }
        return false;
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

    public List<TracedValue> getAll(){
        return record;
    }
}