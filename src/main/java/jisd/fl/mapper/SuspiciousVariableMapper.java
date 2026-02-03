package jisd.fl.mapper;

import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

/**
 * SuspiciousLocalVariable の JSON シリアライズ/デシリアライズを行うマッパー。
 */
public class SuspiciousVariableMapper {
    public static String toJson(SuspiciousLocalVariable suspValue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("failedTest", suspValue.failedTest().toString());
        map.put("locateMethod", suspValue.locateMethod().fullyQualifiedName());
        map.put("variableName", suspValue.variableName(true, true));
        map.put("actualValue", suspValue.actualValue());
        map.put("isPrimitive", suspValue.isPrimitive());
        Gson gson = new Gson();
        return gson.toJson(map);
    }

    public static SuspiciousLocalVariable fromJson(String jsonString){
        return fromJson(new JSONObject(jsonString));
    }

    public static SuspiciousLocalVariable fromJson(JSONObject json){
        MethodElementName failedTest = new MethodElementName(json.getString("failedTest"));
        MethodElementName locateMethod = new MethodElementName(json.getString("locateMethod"));
        boolean isPrimitive = json.getBoolean("isPrimitive");
        String variableName = json.getString("variableName");
        String actualValue = json.getString("actualValue");

        if(variableName.contains("[")){
            int arrayNth = Integer.parseInt(variableName.substring(variableName.indexOf("[") + 1, variableName.indexOf("]")));
            variableName = variableName.substring(0, variableName.indexOf("["));
            return new SuspiciousLocalVariable(
                    failedTest,
                    locateMethod,
                    variableName,
                    actualValue,
                    isPrimitive,
                    arrayNth
            );
        }

        return new SuspiciousLocalVariable(
                failedTest,
                locateMethod,
                variableName,
                actualValue,
                isPrimitive
        );
    }

    public static List<SuspiciousLocalVariable> fromJsonArray(String jsonArrayString){
        return fromJsonArray(new JSONArray(jsonArrayString));
    }

    public static List<SuspiciousLocalVariable> fromJsonArray(JSONArray jsonArray){
        List<SuspiciousLocalVariable> ret = new ArrayList<>();
        for(Object o : jsonArray){
            if(!(o instanceof JSONObject)){
                throw new RuntimeException("SuspiciousVariableMapper.fromJsonArray: invalid jsonArray \n[content]\n" + o.toString());
            }
            ret.add(fromJson((JSONObject) o));
        }
        return ret;
    }
}
