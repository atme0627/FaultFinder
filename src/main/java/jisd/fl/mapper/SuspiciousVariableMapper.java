package jisd.fl.mapper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousFieldVariable;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.core.entity.susp.SuspiciousVariable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SuspiciousVariable の JSON シリアライズ/デシリアライズを行うマッパー。
 */
public class SuspiciousVariableMapper {
    private static final Gson GSON = new Gson();

    public static String toJson(SuspiciousVariable susp) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("failedTest", susp.failedTest().toString());

        switch (susp) {
            case SuspiciousLocalVariable local -> {
                map.put("locateMethod", local.locateMethod().fullyQualifiedName());
            }
            case SuspiciousFieldVariable field -> {
                map.put("locateClass", field.locateClass().fullyQualifiedName());
            }
        }

        map.put("variableName", susp.variableName(true, true));
        map.put("actualValue", susp.actualValue());
        map.put("isPrimitive", susp.isPrimitive());

        switch (susp) {
            case SuspiciousLocalVariable _ -> map.put("type", "local");
            case SuspiciousFieldVariable _ -> map.put("type", "field");
        }

        return GSON.toJson(map);
    }

    public static SuspiciousVariable fromJson(String jsonString) {
        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
        return fromJson(json);
    }

    public static SuspiciousVariable fromJson(JsonObject json) {
        String type = json.get("type").getAsString();
        MethodElementName failedTest = new MethodElementName(json.get("failedTest").getAsString());
        boolean isPrimitive = json.get("isPrimitive").getAsBoolean();
        String variableName = json.get("variableName").getAsString();
        String actualValue = json.get("actualValue").getAsString();

        // 配列インデックスのパース
        int arrayNth = -1;
        if (variableName.contains("[")) {
            arrayNth = Integer.parseInt(
                    variableName.substring(variableName.indexOf("[") + 1, variableName.indexOf("]")));
            variableName = variableName.substring(0, variableName.indexOf("["));
        }

        // this. プレフィックスを除去
        if (variableName.startsWith("this.")) {
            variableName = variableName.substring(5);
        }

        return switch (type) {
            case "local" -> {
                MethodElementName locateMethod = new MethodElementName(json.get("locateMethod").getAsString());
                yield new SuspiciousLocalVariable(
                        failedTest, locateMethod, variableName, actualValue, isPrimitive, arrayNth);
            }
            case "field" -> {
                ClassElementName locateClass = new ClassElementName(json.get("locateClass").getAsString());
                yield new SuspiciousFieldVariable(
                        failedTest, locateClass, variableName, actualValue, isPrimitive, arrayNth);
            }
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }

    public static List<SuspiciousVariable> fromJsonArray(String jsonArrayString) {
        JsonArray jsonArray = JsonParser.parseString(jsonArrayString).getAsJsonArray();
        return fromJsonArray(jsonArray);
    }

    public static List<SuspiciousVariable> fromJsonArray(JsonArray jsonArray) {
        List<SuspiciousVariable> ret = new ArrayList<>();
        for (var element : jsonArray) {
            if (!element.isJsonObject()) {
                throw new RuntimeException(
                        "SuspiciousVariableMapper.fromJsonArray: invalid jsonArray \n[content]\n" + element);
            }
            ret.add(fromJson(element.getAsJsonObject()));
        }
        return ret;
    }
}
