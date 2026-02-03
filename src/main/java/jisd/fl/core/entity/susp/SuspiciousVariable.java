package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;

/**
 * 疑わしい変数を表す sealed interface。
 *
 * ローカル変数かフィールドかは型で区別する（SuspiciousLocalVariable / SuspiciousFieldVariable）。
 * 呼び出し側で型による分岐が必要な場合は switch 式を使用すること。
 */
public sealed interface SuspiciousVariable permits SuspiciousLocalVariable, SuspiciousFieldVariable {
    MethodElementName failedTest();
    ClassElementName locateClass();
    String variableName();
    String actualValue();
    boolean isPrimitive();
    boolean isArray();
    int arrayNth(); // 非配列は -1

    default String variableName(boolean withThis, boolean withArray) {
        String head = switch (this) {
            case SuspiciousFieldVariable _ when withThis -> "this.";
            case SuspiciousFieldVariable _, SuspiciousLocalVariable _ -> "";
        };
        String arr = (isArray() && withArray) ? "[" + arrayNth() + "]" : "";
        return head + variableName() + arr;
    }
}
