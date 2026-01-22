package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;

import java.util.Objects;

public final class SuspiciousFieldVariable implements SuspiciousVariable {
    private final MethodElementName failedTest;
    private final ClassElementName locateClass;
    private final String variableName;
    private final boolean isPrimitive;
    private final boolean isArray;
    private final int arrayNth;
    private final String actualValue;

    public SuspiciousFieldVariable(
            MethodElementName failedTest,
            ClassElementName locateClass,
            String variableName,
            String actualValue,
            boolean isPrimitive) {
        this(failedTest, locateClass, variableName, actualValue, isPrimitive, -1);
    }

    public SuspiciousFieldVariable(
            MethodElementName failedTest,
            ClassElementName locateClass,
            String variableName,
            String actualValue,
            boolean isPrimitive,
            int arrayNth) {
        this.failedTest = failedTest;
        this.locateClass = locateClass;
        this.variableName = variableName;
        this.isPrimitive = isPrimitive;
        this.isArray = arrayNth >= 1;
        this.arrayNth = arrayNth;
        this.actualValue = actualValue;
    }

    @Override public MethodElementName failedTest() { return failedTest; }
    @Override public ClassElementName locateClass() { return locateClass; }
    @Override public String variableName() { return variableName; }
    @Override public String actualValue() { return actualValue; }
    @Override public boolean isPrimitive() { return isPrimitive; }
    @Override public boolean isArray() { return isArray; }
    @Override public int arrayNth() { return arrayNth; }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof SuspiciousFieldVariable vi)) return false;

        return this.variableName.equals(vi.variableName) &&
                this.isPrimitive == vi.isPrimitive &&
                this.arrayNth == vi.arrayNth &&
                this.isArray == vi.isArray &&
                this.actualValue.equals(vi.actualValue) &&
                this.locateClass.equals(vi.locateClass);
    }

    @Override
    public int hashCode(){
        return Objects.hash(
                variableName,
                isPrimitive,
                arrayNth,
                isArray,
                actualValue,
                locateClass
        );
    }

    @Override
    public String toString() {
        return  "     [LOCATION] " + locateClass +
                " [PROBE TARGET] " + variableName(true, true) + " == " + actualValue();
    }
}
