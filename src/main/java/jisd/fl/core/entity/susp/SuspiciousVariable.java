package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;

public sealed interface SuspiciousVariable permits SuspiciousLocalVariable, SuspiciousFieldVariable {
    MethodElementName failedTest();
    ClassElementName locateClass();
    String variableName();
    String actualValue();
    boolean isPrimitive();
    boolean isArray();
    int arrayNth(); // 非配列は -1

    default String variableName(boolean withThis, boolean withArray) {
        String head = (this instanceof SuspiciousFieldVariable && withThis) ? "this." : "";
        String arr = (isArray() && withArray) ? "[" + arrayNth() + "]" : "";
        return head + variableName() + arr;
    }

}
