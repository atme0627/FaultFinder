package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;

import java.util.Objects;

/**
 * ローカル変数を表すクラス。
 */
public final class SuspiciousLocalVariable implements SuspiciousVariable {
    private final MethodElementName failedTest;
    private final MethodElementName locateMethod;
    private final String variableName;
    private final boolean isPrimitive;
    private final boolean isArray;
    private final int arrayNth;
    private final String actualValue;

    /** 配列でない場合のコンストラクタ */
    public SuspiciousLocalVariable(
            MethodElementName failedTest,
            String locateMethod,
            String variableName,
            String actualValue,
            boolean isPrimitive) {
        this(failedTest, locateMethod, variableName, actualValue, isPrimitive, -1);
    }

    /** 配列の場合のコンストラクタ */
    public SuspiciousLocalVariable(
            MethodElementName failedTest,
            String locateMethod,
            String variableName,
            String actualValue,
            boolean isPrimitive,
            int arrayNth) {

        this.failedTest = failedTest;
        this.locateMethod = new MethodElementName(locateMethod);
        this.variableName = variableName;
        this.isPrimitive = isPrimitive;
        this.arrayNth = arrayNth;
        this.isArray = (arrayNth >= 0);
        this.actualValue = actualValue;
    }

    @Override public MethodElementName failedTest() { return failedTest; }
    @Override public ClassElementName locateClass() { return locateMethod.classElementName; }
    @Override public String actualValue() { return actualValue; }
    @Override public boolean isPrimitive() { return isPrimitive; }
    @Override public boolean isArray() { return isArray; }
    @Override public int arrayNth() { return arrayNth; }
    @Override public String variableName() { return variableName; }

    public MethodElementName locateMethod() {
        return locateMethod;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof SuspiciousLocalVariable vi)) return false;

        return this.variableName.equals(vi.variableName) &&
                this.isPrimitive == vi.isPrimitive &&
                this.arrayNth == vi.arrayNth &&
                this.isArray == vi.isArray &&
                this.actualValue.equals(vi.actualValue) &&
                this.locateMethod.equals(vi.locateMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                variableName,
                isPrimitive,
                arrayNth,
                isArray,
                actualValue,
                locateMethod
        );
    }

    @Override
    public String toString() {
        return "     [LOCATION] " + locateMethod +
                " [PROBE TARGET] " + variableName(true, true) + " == " + actualValue();
    }
}