package jisd.fl.probe.record;

import com.sun.jdi.*;
import jisd.debug.Location;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.assertinfo.VariableInfo;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

//valueInfoを受け取って、TracedValueに変換する責務を持つ
public abstract class TracedValueCollection {
    protected List<TracedValue> record;

    protected TracedValueCollection(){
    }

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

    protected List<TracedValue> convertValueInfo(ValueInfo vi, Location loc){
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

    protected List<TracedValue> convertArrayInfo(ValueInfo vi, Location loc){
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

    protected List<TracedValue> convertPrimitiveInfo(ValueInfo vi, Location loc){
        return List.of(new TracedValue(
                vi.getCreatedAt(),
                loc,
                vi.getName(),
                vi.getValue()
        ));
    }

    protected List<TracedValue> convertPrimitiveWrapperInfo(ValueInfo vi, Location loc){
        ValueInfo primitiveValue = vi.getField("value");
        return convertPrimitiveInfo(primitiveValue, loc);
    }

    //暫定
    protected List<TracedValue> convertReferenceInfo(ValueInfo vi, Location loc){
        return List.of(new TracedValue(
                vi.getCreatedAt(),
                loc,
                vi.getName(),
                vi.getValue()
        ));
    }

    protected boolean isPrimitiveWrapper(Type type) {
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