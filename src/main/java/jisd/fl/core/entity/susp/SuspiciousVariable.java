package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.MethodElementName;

import java.util.Objects;

public class SuspiciousVariable {
    //ローカル変数の場合のみ
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

    public String getLocateClass() {
        return locateMethod.fullyQualifiedClassName();
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

    public String getActualValue() {
        return actualValue;
    }

    public MethodElementName getFailedTest() {
        return failedTest;
    }

    public String getLocateMethod(boolean withClass) {
        if (withClass) {
            return locateMethod.fullyQualifiedName();
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

    @Override
    public String toString() {
        return  "     [LOCATION] " + locateMethod +
                " [PROBE TARGET] " + getVariableName(true, true) + " == " + getActualValue();

    }

    public SuspiciousExpression getParent() {
        return parent;
    }

    public void setParent(SuspiciousExpression parent) {
        this.parent = parent;
    }

}
