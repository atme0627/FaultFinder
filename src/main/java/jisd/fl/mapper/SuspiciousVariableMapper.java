package jisd.fl.mapper;

import jisd.fl.core.entity.MethodElementName;
import jisd.fl.probe.info.SuspiciousVariable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

public class SuspiciousVariableMapper {
    public static String toJson(SuspiciousVariable suspValue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("failedTest", suspValue.getFailedTest().toString());
        map.put("locateMethod", suspValue.getLocateMethod(true));
        map.put("variableName", suspValue.getVariableName(true, true));
        map.put("actualValue", suspValue.getActualValue());
        map.put("isPrimitive", suspValue.isPrimitive());
        map.put("isField", suspValue.isField());
        Gson gson = new Gson();
        return gson.toJson(map);
    }

    public static SuspiciousVariable fromJson(String jsonString){
        return fromJson(new JSONObject(jsonString));
    }
    public static SuspiciousVariable fromJson(JSONObject json){
            MethodElementName failedTest = new MethodElementName(json.getString("failedTest"));
            MethodElementName locateMethod = new MethodElementName(json.getString("locateMethod"));
            boolean isPrimitive = json.getBoolean("isPrimitive");
            String variableName = json.getString("variableName");
            String actualValue = json.getString("actualValue");
            boolean isField = json.getBoolean("isField");

            if(variableName.contains("[")){
                int arrayNth = Integer.parseInt(variableName.substring(variableName.indexOf("[") + 1, variableName.indexOf("]")));
                variableName = variableName.substring(0, variableName.indexOf("["));
                return new SuspiciousVariable(
                        failedTest,
                        locateMethod.getFullyQualifiedMethodName(),
                        variableName,
                        actualValue,
                        isPrimitive,
                        isField,
                        arrayNth
                );
            }

            return new SuspiciousVariable(
                    failedTest,
                    locateMethod.getFullyQualifiedMethodName(),
                    variableName,
                    actualValue,
                    isPrimitive,
                    isField
            );
    }

    public static List<SuspiciousVariable> fromJsonArray(String jsonArrayString){
        return fromJsonArray(new JSONArray(jsonArrayString));
    }
    public static List<SuspiciousVariable> fromJsonArray(JSONArray jsonArray){
        List<SuspiciousVariable> ret = new ArrayList<>();
        for(Object o : jsonArray){
            if(!(o instanceof JSONObject)){
                throw new RuntimeException("SuspiciousVariableMapper.fromJsonArray: invalid jsonArray \n[content]\n" + o.toString());
            }
            ret.add(fromJson((JSONObject) o));
        }
        return ret;
    }
}
