package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;

public final class SuspiciousFieldVariable implements SuspiciousVariable {
    @Override
    public MethodElementName failedTest() {
        return null;
    }

    @Override
    public ClassElementName locateClass() {
        return null;
    }

    @Override
    public String actualValue() {
        return "";
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public int arrayNth() {
        return 0;
    }
}
