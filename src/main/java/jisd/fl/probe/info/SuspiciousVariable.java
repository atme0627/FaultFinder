package jisd.fl.probe.info;

import com.fasterxml.jackson.annotation.*;
import jisd.fl.core.entity.MethodElementName;
import org.json.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SuspiciousVariable {
    //ローカル変数の場合のみ
    private final MethodElementName failedTest;
    @JsonProperty("locateMethodElement")
    private final MethodElementName locateMethod;
    @JsonProperty("simpleVariableName")
    private final String variableName;
    @JsonProperty("primitive")
    private final boolean isPrimitive;
    @JsonProperty("field")
    private final boolean isField;
    @JsonProperty("array")
    private final boolean isArray;
    private final int arrayNth;
    private final String actualValue;

    //木構造でスライスを表すため、このSuspVarの親の参照を保持する。
    private SuspiciousExpression parent = null;

    //配列でない場合
    public SuspiciousVariable(
            MethodElementName failedTest,
            String locateMethod,
            String variableName,
            String actualValue,
            boolean isPrimitive,
            boolean isField) {
        this(failedTest, locateMethod, variableName, actualValue, isPrimitive, isField, -1);
    }
    
    //locateはローカル変数の場合はメソッド名まで(フルネーム、シグニチャあり)
    //フィールドの場合はクラス名まで
    //配列の場合
    public SuspiciousVariable(
            MethodElementName failedTest,
            String locateMethod,
            String variableName,
            String actualValue,
            boolean isPrimitive,
            boolean isField,
            int arrayNth) {

        this.failedTest = failedTest;
        this.locateMethod = new MethodElementName(locateMethod);
        this.variableName = variableName;
        this.isPrimitive = isPrimitive;
        this.isField = isField;
        this.arrayNth = arrayNth;
        this.isArray = (arrayNth >= 0);
        this.actualValue = actualValue;
    }

    @JsonCreator
    protected SuspiciousVariable(
            @JsonProperty("failedTest") MethodElementName failedTest,
            @JsonProperty("locateMethod") MethodElementName locateMethod,
            @JsonProperty("variableName") String variableName,
            @JsonProperty("isPrimitive") boolean isPrimitive,
            @JsonProperty("isField") boolean isField,
            @JsonProperty("isArray") boolean isArray,
            @JsonProperty("arrayNth") int arrayNth,
            @JsonProperty("actualValue") String actualValue
    ) {
        this.failedTest = failedTest;
        this.locateMethod = locateMethod;
        this.variableName = variableName;
        this.isPrimitive = isPrimitive;
        this.isField = isField;
        this.isArray = isArray;
        this.arrayNth = arrayNth;
        this.actualValue = actualValue;
    }

    public String getLocateClass() {
        return locateMethod.getFullyQualifiedClassName();
    }

    public String getSimpleVariableName() {
        return getVariableName(false, false);
    }

    public String getVariableName(boolean withThis, boolean withArray) {
        return (isField() && withThis ? "this." : "") + variableName + (isArray() && withArray ? "[" + arrayNth + "]" : "");
    }

    public boolean isArray() {
        return isArray;
    }

    public boolean isPrimitive() {
        return isPrimitive;
    }

    public boolean isField() {
        return isField;
    }

    public int getArrayNth() {
        return arrayNth;
    }

    public String getLocateMethod(boolean withClass) {
        if (withClass) {
            return locateMethod.getFullyQualifiedMethodName();
        } else {
            return locateMethod.methodSignature;
        }
    }

    public MethodElementName getLocateMethodElement() {
        return locateMethod;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof SuspiciousVariable)) return false;
        SuspiciousVariable vi = (SuspiciousVariable) obj;

        return this.variableName.equals(vi.variableName) &&
                        this.isPrimitive == vi.isPrimitive &&
                        this.isField == vi.isField &&
                        this.arrayNth == vi.arrayNth &&
                        this.isArray == vi.isArray &&
                        this.actualValue.equals(vi.actualValue) &&
                        this.locateMethod.equals(vi.locateMethod);
    }

    @Override
    public int hashCode(){
        return Objects.hash(
                variableName,
                isPrimitive,
                isField,
                arrayNth,
                isArray,
                actualValue,
                locateMethod
        );
    }

    public String getActualValue() {
        return actualValue;
    }

    @Override
    public String toString() {
        return  "     [LOCATION] " + locateMethod +
                " [PROBE TARGET] " + getVariableName(true, true) + " == " + getActualValue();

    }

    public MethodElementName getFailedTest() {
        return failedTest;
    }

    public SuspiciousExpression getParent() {
        return parent;
    }

    public void setParent(SuspiciousExpression parent) {
        this.parent = parent;
    }

    /**
     * @return Json
     * {
     *     failedTest: org.example.sampleTest
     *     locateMethod: org.example.sample#sampleMethod(x, y)
     *     isPrimitive: true
     *     isArray: false
     *     ArrayIndex: 10 (option)
     *     variableName: result
     *     actualValue: 10
     * }
     */
    public JSONObject toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("failedTest", failedTest.toString());
        map.put("locateMethod", locateMethod.toString());
        map.put("isPrimitive", isPrimitive);
        map.put("isArray", isArray);
        if (isArray) {
            map.put("ArrayIndex", arrayNth);
        }
        map.put("variableName", variableName);
        map.put("actualValue", actualValue);
        return new JSONObject(map);
    }

    static public SuspiciousVariable fromJson(String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);
            MethodElementName failedTest = new MethodElementName(json.getString("failedTest"));
            MethodElementName locateMethod = new MethodElementName(json.getString("locateMethod"));
            boolean isPrimitive = json.getBoolean("isPrimitive");
            boolean isArray = json.getBoolean("isArray");
            int arrayNth = isArray ? json.getInt("ArrayIndex") : -1;
            String variableName = json.getString("variableName");
            String actualValue = json.getString("actualValue");

            return new SuspiciousVariable(
                    failedTest,
                    locateMethod.getFullyQualifiedMethodName(),
                    variableName,
                    actualValue,
                    isPrimitive,
                    false,
                    arrayNth
            );
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
