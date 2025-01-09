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
            //Optional<DebugResult> od = p.getResults(varName);
            HashMap<String, DebugResult> drs = p.getResults();
            //if (od.isEmpty()) continue;
            watchedValues.addElements(getValuesFromDebugResult(variableInfo, drs));
        }

        if (watchedValues.isEmpty()) {

            throw new RuntimeException("Probe#runTest\n" +
                    "there is not target value in watch point.");
        }
        return watchedValues;
    }

    //primitive型の値のみを取得
    //variableInfoが参照型の場合、fieldを取得してその中から目的のprimitive型の値を探す
    private List<AbstractProbe.ProbeInfo> getValuesFromDebugResult(VariableInfo targetInfo, HashMap<String, DebugResult> drs){
        List<AbstractProbe.ProbeInfo> pis = new ArrayList<>();
        drs.forEach((variable, dr) -> {
            List<ValueInfo> vis = null;
            try {
                vis = new ArrayList<>(dr.getValues());
            } catch (RuntimeException e) {
                return;
            }

            VariableInfo variableInfo = variable.equals(targetInfo.getVariableName(true, false)) ? targetInfo : null;

            for (ValueInfo vi : vis) {
                LocalDateTime createdAt = vi.getCreatedAt();
                Location loc = dr.getLocation();
                String variableName = (variableInfo == null) ? vi.getName() : variableInfo.getVariableName(true, true);
                String value;
                //対象の変数がnullの場合
                if (vi.getValue().isEmpty()) {
                    value = "null";
                    pis.add(new AbstractProbe.ProbeInfo(createdAt, loc, variableName, value));
                } else {
                    //viがprobe対象
                    if(variableInfo != null){
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
                }
            }
        });
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
        String valueType = law.substring("instance of".length(), law.indexOf("(")).trim();
        return primitiveWrapper.contains(valueType);
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
            //actualがnullの場合
            if(variableInfo.getActualValue().equals("null")){
                //System.err.println(vi.ch().get(0).getValue());
                return new PrimitiveInfo(vi.getName(), vi.getStratum(), vi.getCreatedAt(), vi.getValue());
            }
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
        String law = vi.getValue();
        String valueType = law.substring("instance of".length(), law.indexOf("(")).trim();

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
            ArrayList<PrimitiveInfo> pis = new ArrayList<>();
            vi.ch().forEach(e -> pis.add(getPrimitiveInfoFromPrimitiveType(e)));
            return pis;
        }
        throw new RuntimeException(vi.getName() + "is not array.");
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
        String valueType = law.substring("instance of".length(), law.indexOf("(")).trim();

        //プリミティブ型のラッパークラスのとき
        if(primitiveWrapper.contains(valueType)) {
            return (PrimitiveInfo) vi.ch().get(0);
        }

        throw new RuntimeException(vi.getName() + " is not primitive.");
    }
}
