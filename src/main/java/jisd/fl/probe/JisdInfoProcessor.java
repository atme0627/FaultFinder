package jisd.fl.probe;

import jisd.debug.DebugResult;
import jisd.debug.Location;
import jisd.debug.Point;
import jisd.debug.value.PrimitiveInfo;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.AbstractProbe.ProbeInfoCollection;
import jisd.fl.probe.assertinfo.VariableInfo;

import java.time.LocalDateTime;
import java.util.*;

public class JisdInfoProcessor {

    public ProbeInfoCollection getInfoFromWatchPoints(List<Optional<Point>> watchPoints, VariableInfo variableInfo){
        //get Values from debugResult
        //実行されなかった行の情報は飛ばす。
        //実行されたがnullのものは含む。
        String varName = variableInfo.getVariableName(true, false);
        ProbeInfoCollection watchedValues = new ProbeInfoCollection();
        for (Optional<Point> op : watchPoints) {
            Point p;
            if (op.isEmpty()) continue;
            p = op.get();
            HashMap<String, DebugResult> drs = p.getResults();
            watchedValues.addElements(getValuesFromDebugResults(variableInfo, drs));
        }


        if (watchedValues.isEmpty()) {
            throw new RuntimeException("there is not target value in watch point.");
        }
        return watchedValues;
    }

    //primitive型の値のみを取得
    //variableInfoが参照型の場合、fieldを取得してその中から目的のprimitive型の値を探す
    public List<AbstractProbe.ProbeInfo> getValuesFromDebugResults(VariableInfo targetInfo, HashMap<String, DebugResult> drs){
        List<AbstractProbe.ProbeInfo> pis = new ArrayList<>();
        drs.forEach((variable, dr) -> {
            VariableInfo variableInfo = variable.equals(targetInfo.getVariableName(true, false)) ? targetInfo : null;
            pis.addAll(getValuesFromDebugResult(variableInfo, dr));
        });
        return pis;
    }

    public List<AbstractProbe.ProbeInfo> getValuesFromDebugResult(VariableInfo variableInfo, DebugResult dr) {
        List<ValueInfo> vis = null;
        List<AbstractProbe.ProbeInfo> pis = new ArrayList<>();
        try {
            vis = new ArrayList<>(dr.getValues());
        } catch (RuntimeException e) {
            return null;
        }

        for (ValueInfo vi : vis) {
            LocalDateTime createdAt = vi.getCreatedAt();
            Location loc = dr.getLocation();
            String variableName = (variableInfo == null) ? vi.getName() : variableInfo.getVariableName(true, false);
            String value;
            //対象の変数がnullの場合
            if (vi.getValue().isEmpty()) {
                value = "null";
                pis.add(new AbstractProbe.ProbeInfo(createdAt, loc, variableName, value));
            } else {
                //viがprobe対象
                if(variableInfo != null && variableInfo.getTargetField() != null){
                    value = getPrimitiveInfoFromReferenceType(vi, variableInfo).getValue();
                    pis.add(new AbstractProbe.ProbeInfo(createdAt, loc, variableName, value));
                }
                //viがプリミティブ型の一次元配列
                else if(vi.getValue().contains("[") && !vi.getValue().contains("][")) {
                    List<PrimitiveInfo> piList = getPrimitiveInfoFromArrayType(vi);
                    for(int i = 0; i < piList.size(); i++){
                        value = piList.get(i).getValue();
                        pis.add(new AbstractProbe.ProbeInfo(createdAt, loc, variableName + "[" + i + "]", value));
                    }
                }

                //viがプリミティブ型かそのラッパー
                else if(isPrimitive(vi)) {
                    value = getPrimitiveInfoFromPrimitiveType(vi).getValue();
                    pis.add(new AbstractProbe.ProbeInfo(createdAt, loc, variableName, value));
                }

                else {
                    //viが参照型
                    //actualがnullかinstance ofの場合;
                    value = vi.getValue();
                    if (value.contains("(id")) {
                        value = value.split("\\(")[0];
                    }
                    pis.add(new AbstractProbe.ProbeInfo(vi.getCreatedAt(), loc, vi.getName(), value));
                }
            }
        }
        return pis;
    }


    private boolean isPrimitive(ValueInfo vi){
        Set<String> primitiveWrapper = new HashSet<>(List.of(
                "java.lang.Boolean",
                "java.lang.Byte",
                "java.lang.Character",
                "java.lang.Double",
                "java.lang.Float",
                "java.lang.Integer",
                "java.lang.Long",
                "java.lang.Short"
        ));

        if (vi instanceof PrimitiveInfo) return true;

        String law = vi.getValue();
        if(law.contains("instance")) {
            String valueType = law.substring("instance of".length(), law.indexOf("(")).trim();
            return primitiveWrapper.contains(valueType);

        }

        //primitive型でもfieldの場合はObjectInfo型になるっぽい
        return true;
    }

    //参照型の配列には未対応
    private PrimitiveInfo getPrimitiveInfoFromReferenceType(ValueInfo vi, VariableInfo variableInfo){
        //プリミティブ型の配列の場合
        if(variableInfo.isArray()){
            int arrayNth = variableInfo.getArrayNth();
            ArrayList<ValueInfo> arrayElements = vi.ch();
            return (PrimitiveInfo) arrayElements.get(arrayNth);
        }

        //プリミティブ型の場合
        if(variableInfo.isPrimitive()) {
            return getPrimitiveInfoFromPrimitiveType(vi);
        }
        //参照型の場合
        else {
            ArrayList<ValueInfo> fieldElements = vi.ch();
            boolean isFound = false;
            String fieldName = variableInfo.getTargetField().getVariableName();
            for(ValueInfo e : fieldElements){
                if(e.getName().equals(fieldName)){
                    getPrimitiveInfoFromReferenceType(e, variableInfo.getTargetField());
                    isFound = true;
                    break;
                }
            }
            if(!isFound) throw new NoSuchElementException(fieldName + " is not found in fields of" + variableInfo.getVariableName(false, false));
        }

        throw new RuntimeException();
    }


    private ArrayList<PrimitiveInfo> getPrimitiveInfoFromArrayType(ValueInfo vi) {
        //    vi.getValue() --> instance of double[1] (id=2814)
        ArrayList<PrimitiveInfo> pis = new ArrayList<>();
        String law = vi.getValue();
        String valueType;
        try {
            valueType = law.substring("instance of".length(), law.indexOf("(")).trim();
        }
        catch(StringIndexOutOfBoundsException e){
            System.err.println(e);
            System.err.println("value: " + law);
            return pis;
        }

        Set<String> primitiveType = new HashSet<>(List.of(
                "boolean",
                "byte",
                "char",
                "double",
                "float",
                "int",
                "long",
                "short"
        ));

        //プリミティブ型の配列のとき
        if(primitiveType.contains(valueType.substring(0, valueType.indexOf("[")))){
            vi.ch().forEach(e -> pis.add(getPrimitiveInfoFromPrimitiveType(e)));
            return pis;
        }
        System.err.println(vi.getName() + " is not primitive array.");
        return pis;
    }



    //viがprimitive型とそのラッパー型である場合のみ考える。
    //そうでない場合はnullを返す。
    private PrimitiveInfo getPrimitiveInfoFromPrimitiveType(ValueInfo vi) {
        if(vi instanceof PrimitiveInfo) return (PrimitiveInfo) vi;

        Set<String> primitiveWrapper = new HashSet<>(List.of(
                "java.lang.Boolean",
                "java.lang.Byte",
                "java.lang.Character",
                "java.lang.Double",
                "java.lang.Float",
                "java.lang.Integer",
                "java.lang.Long",
                "java.lang.Short"
        ));

        //ex) vi.getValue() --> instance of java.lang.Integer(id=2827)
        String law = vi.getValue();
        if(law.contains("instance")) {
            String valueType = law.substring("instance of".length(), law.indexOf("(")).trim();
            //プリミティブ型のラッパークラスのとき
            if(primitiveWrapper.contains(valueType)) {
                return (PrimitiveInfo) vi.ch().get(0);
            }
        }

        //primitive型でもfieldの場合はObjectInfo型になるっぽい
        return new PrimitiveInfo(vi.getName(), vi.getStratum(), vi.getCreatedAt(), vi.getValue());

        //throw new RuntimeException(vi.getName() + " is not primitive.");
    }
}
