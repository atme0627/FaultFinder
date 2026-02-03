package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;

import java.util.Objects;

/**
 * ローカル変数を表す record。
 */
public record SuspiciousLocalVariable(
    MethodElementName failedTest,
    MethodElementName locateMethod,
    String variableName,
    String actualValue,
    boolean isPrimitive,
    int arrayNth
) implements SuspiciousVariable {

    /** 非配列の場合のコンストラクタ */
    public SuspiciousLocalVariable(
            MethodElementName failedTest,
            MethodElementName locateMethod,
            String variableName,
            String actualValue,
            boolean isPrimitive) {
        this(failedTest, locateMethod, variableName, actualValue, isPrimitive, -1);
    }

    public SuspiciousLocalVariable {
        Objects.requireNonNull(failedTest, "failedTest must not be null");
        Objects.requireNonNull(locateMethod, "locateMethod must not be null");
        Objects.requireNonNull(variableName, "variableName must not be null");
        Objects.requireNonNull(actualValue, "actualValue must not be null");
    }

    @Override
    public boolean isArray() {
        return arrayNth >= 0;
    }

    @Override
    public ClassElementName locateClass() {
        return locateMethod.classElementName;
    }

    @Override
    public String toString() {
        return "     [LOCATION] " + locateMethod +
                " [PROBE TARGET] " + variableName(true, true) + " == " + actualValue();
    }
}