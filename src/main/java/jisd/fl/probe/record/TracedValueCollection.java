package jisd.fl.probe.record;

import com.sun.jdi.*;
import de.vandermeer.asciitable.*;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import jisd.debug.Location;
import jisd.debug.value.ValueInfo;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

//valueInfoを受け取って、TracedValueに変換する責務を持つ
public abstract class TracedValueCollection {
    protected List<TracedValue> record;

    protected TracedValueCollection(){
    }

    protected TracedValueCollection(List<TracedValue> record){
        this.record = record;
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
        if(vi.getJValue() == null) return convertPrimitiveInfo(vi, loc);
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
                    vi.getName() + "[" + i + "]",
                    children.get(i).getValue(),
                    loc.getLineNumber()
            ));
        }
        return result;
    }

    protected List<TracedValue> convertPrimitiveInfo(ValueInfo vi, Location loc){
        return List.of(new TracedValue(
                vi.getCreatedAt(),
                vi.getName(),
                vi.getValue(),
                loc.getLineNumber()
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
                vi.getName(),
                vi.getValue(),
                loc.getLineNumber(),
                true
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
        record.sort(TracedValue::compareTo);
        for(TracedValue tv : record){
            System.out.println("     " + tv);
        }
    }

    public List<TracedValue> getAll(){
        return record;
    }

    public String toTableString(){
        AsciiTable at = new AsciiTable();
        at.addRow("[LINE]", "[VARIABLE]");
        for (TracedValue tv : record){
            at.addRow(tv.lineNumber, tv.variableName + " == " + tv.value);
        }
        at.getRenderer().setCWC(new CWC_LongestLine());
        at.setPaddingLeftRight(1);

        for(AT_Row row : at.getRawContent()){
            List<AT_Cell> cells = row.getCells();
            cells.get(0).getContext().setTextAlignment(TextAlignment.RIGHT);
            cells.get(1).getContext().setTextAlignment(TextAlignment.LEFT);
        }
        return at.render();
    }
}