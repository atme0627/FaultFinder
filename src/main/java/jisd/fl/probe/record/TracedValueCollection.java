package jisd.fl.probe.record;

import com.sun.jdi.*;
import jisd.debug.Location;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.assertinfo.VariableInfo;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

//valueInfoを受け取って、TracedValueに変換する責務を持つ
public class TracedValueCollection {
    private final List<TracedValue> record;

    //特定の行の変数を全て記録
    //次の探索対象を探すために使用
    public TracedValueCollection(List<ValueInfo> valuesAtLine, Location loc){
        record = valuesAtLine.stream()
                .map(vi -> convertValueInfo(vi, loc))
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    //viで指定された変数のみ記録
    //原因行特定のために使用
    public TracedValueCollection(VariableInfo target, List<ValueInfo> valuesOfTarget, Map<LocalDateTime, Location> locationAtTime) {
        record = convertValueInfoOfTarget(target, valuesOfTarget, locationAtTime);
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


    //TODO: primitive型のラッパークラスを考慮
    private List<TracedValue> convertValueInfoOfTarget(VariableInfo target, List<ValueInfo> valuesOfTarget, Map<LocalDateTime, Location> locationAtTime){
        return  valuesOfTarget.stream()
                        .map(v -> convertValueInfoOfTarget(target, v, locationAtTime.get(v.getCreatedAt())))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
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

    private List<TracedValue> convertValueInfoOfTarget(VariableInfo target, ValueInfo vi, Location loc){
        Type typeOfValue = vi.getJValue().type();
        //変数が配列の場合
        if(typeOfValue instanceof ArrayType){
            return convertArrayInfo(target, vi, loc);
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
                    vi.getName() + "[" + i + "]",
                    children.get(i).getValue()
            ));
        }
        return result;
    }

    private List<TracedValue> convertArrayInfo(VariableInfo target, ValueInfo vi, Location loc){
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