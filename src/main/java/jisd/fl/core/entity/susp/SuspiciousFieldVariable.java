package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;

import java.util.Objects;

/**
 * フィールド変数を表す record。
 */
public record SuspiciousFieldVariable(
    MethodElementName failedTest,
    ClassElementName locateClass,
    String variableName,
    String actualValue,
    boolean isPrimitive,
    int arrayNth
) implements SuspiciousVariable {

    /** 非配列の場合のコンストラクタ */
    public SuspiciousFieldVariable(
            MethodElementName failedTest,
            ClassElementName locateClass,
            String variableName,
            String actualValue,
            boolean isPrimitive) {
        this(failedTest, locateClass, variableName, actualValue, isPrimitive, -1);
    }

    public SuspiciousFieldVariable {
        Objects.requireNonNull(failedTest, "failedTest must not be null");
        Objects.requireNonNull(locateClass, "locateClass must not be null");
        Objects.requireNonNull(variableName, "variableName must not be null");
        Objects.requireNonNull(actualValue, "actualValue must not be null");
    }

    @Override
    public boolean isArray() {
        return arrayNth >= 0;  // SuspiciousLocalVariable と統一
    }

    @Override
    public String toString() {
        return "     [LOCATION] " + locateClass +
                " [PROBE TARGET] " + variableName(true, true) + " == " + actualValue();
    }
}