package jisd.fl.mapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jisd.fl.core.entity.element.LineElementName;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.CauseTreeNode;
import jisd.fl.core.entity.susp.ExpressionType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CauseTreeNode の JSON シリアライズ/デシリアライズを行うマッパー。
 */
public class CauseTreeMapper {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static String toJson(CauseTreeNode root) {
        return GSON.toJson(toJsonObject(root));
    }

    private static Map<String, Object> toJsonObject(CauseTreeNode node) {
        Map<String, Object> map = new LinkedHashMap<>();

        if (node.location() != null) {
            map.put("location", formatLocation(node.location()));
            map.put("stmtString", node.stmtString());
            map.put("actualValue", node.actualValue());
        }

        if (!node.children().isEmpty()) {
            map.put("children", node.children().stream()
                    .map(CauseTreeMapper::toJsonObject)
                    .toList());
        }

        if (node.type() != null) {
            map.put("type", node.type().name().toLowerCase());
        }

        return map;
    }

    private static String formatLocation(LineElementName location) {
        return location.methodElementName.fullyQualifiedName() + ":" + location.line;
    }

    public static CauseTreeNode fromJson(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return fromJsonObject(obj);
    }

    private static CauseTreeNode fromJsonObject(JsonObject json) {
        ExpressionType type = null;
        LineElementName location = null;
        String stmtString = null;
        String actualValue = null;

        if (json.has("type")) {
            type = ExpressionType.valueOf(json.get("type").getAsString().toUpperCase());
        }

        if (json.has("location")) {
            location = parseLocation(json.get("location").getAsString());
        }

        if (json.has("stmtString")) {
            stmtString = json.get("stmtString").getAsString();
        }

        if (json.has("actualValue")) {
            actualValue = json.get("actualValue").getAsString();
        }

        CauseTreeNode node = new CauseTreeNode(type, location, stmtString, actualValue);

        if (json.has("children")) {
            JsonArray children = json.getAsJsonArray("children");
            for (var childElement : children) {
                node.addChildNode(fromJsonObject(childElement.getAsJsonObject()));
            }
        }

        return node;
    }

    private static LineElementName parseLocation(String locationStr) {
        // format: "com.example.Foo#bar(int):42"
        int colonIndex = locationStr.lastIndexOf(':');
        if (colonIndex == -1) {
            throw new IllegalArgumentException("Invalid location format: " + locationStr);
        }
        String methodPart = locationStr.substring(0, colonIndex);
        int line = Integer.parseInt(locationStr.substring(colonIndex + 1));
        return new LineElementName(new MethodElementName(methodPart), line);
    }
}
