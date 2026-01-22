package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;

import java.util.Objects;

//TODO: fieldで使っている部分をSuspiciousFieldVariableに置き換える。
public final class SuspiciousLocalVariable implements SuspiciousVariable{
    //ローカル変数の場合のみ
    private final MethodElementName failedTest;
    private final MethodElementName locateMethod;
    private final String variableName;
    private final boolean isPrimitive;
    private final boolean isField;
    private final boolean isArray;
    private final int arrayNth;
    private final String actualValue;

    //配列でない場合
    public SuspiciousLocalVariable(
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
    public SuspiciousLocalVariable(
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

    @Override public MethodElementName failedTest() { return failedTest; }
    @Override public ClassElementName locateClass() { return locateMethod.classElementName;}
    @Override public String actualValue() { return actualValue; }
    @Override public boolean isPrimitive() { return isPrimitive; }
    @Override public boolean isArray() { return isArray; }
    @Override public int arrayNth() { return arrayNth; }
    @Override  public String variableName() { return variableName; }

    public MethodElementName locateMethod() {
        return locateMethod;
    }

    @Deprecated
    public String getLocateClass() {
        return locateMethod.fullyQualifiedClassName();
    }
    @Deprecated
    public boolean isField() {
        return isField;
    }
    @Deprecated
    public String getLocateMethodString(boolean withClass) {
        if (withClass) {
            return locateMethod.fullyQualifiedName();
        } else {
            return locateMethod.methodSignature;
        }
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof SuspiciousLocalVariable)) return false;
        SuspiciousLocalVariable vi = (SuspiciousLocalVariable) obj;

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
                " [PROBE TARGET] " + variableName(true, true) + " == " + actualValue();
    }
}
