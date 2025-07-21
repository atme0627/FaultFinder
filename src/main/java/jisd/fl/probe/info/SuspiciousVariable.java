package jisd.fl.probe.info;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jisd.fl.util.analyze.MethodElementName;

import java.util.List;
import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)

public class SuspiciousVariable { //ローカル変数の場合のみ
    private final MethodElementName failedTest;
    private final MethodElementName locateMethod;
    private final String variableName;
    private final boolean isPrimitive;
    private final boolean isField;
    private final boolean isArray;
    private final int arrayNth;
    private final String actualValue;

    //木構造でスライスを表すため、このSuspVarの親の参照を保持する。
    private SuspiciousExpression parent = null;

    //locateはローカル変数の場合はメソッド名まで(フルネーム、シグニチャあり)
    //フィールドの場合はクラス名まで
    //配列の場合
    @Deprecated
    public SuspiciousVariable(String locateMethod,
                              String variableName,
                              String actualValue,
                              boolean isPrimitive,
                              boolean isField,
                              int arrayNth) {
        failedTest = new MethodElementName("EMPTY");
        this.locateMethod = new MethodElementName(locateMethod);
        this.variableName = variableName;
        this.isPrimitive = isPrimitive;
        this.isField = isField;
        this.arrayNth = arrayNth;
        this.isArray = true;
        this.actualValue = actualValue;
    }

    //配列でない場合
    @Deprecated
    public SuspiciousVariable(String locateMethod,
                              String variableName,
                              String actualValue,
                              boolean isPrimitive,
                              boolean isField) {
        failedTest = new MethodElementName("EMPTY");
        this.locateMethod = new MethodElementName(locateMethod);
        this.variableName = variableName;
        this.isPrimitive = isPrimitive;
        this.isField = isField;
        this.arrayNth = -1;
        this.isArray = false;
        this.actualValue = actualValue;
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
        this.isArray = true;
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

    //配列でない場合
    public SuspiciousVariable(
            MethodElementName failedTest,
            String locateMethod,
            String variableName,
            String actualValue,
            boolean isPrimitive,
            boolean isField) {

        this.failedTest = failedTest;
        this.locateMethod = new MethodElementName(locateMethod);
        this.variableName = variableName;
        this.isPrimitive = isPrimitive;
        this.isField = isField;
        this.arrayNth = -1;
        this.isArray = false;
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
}
