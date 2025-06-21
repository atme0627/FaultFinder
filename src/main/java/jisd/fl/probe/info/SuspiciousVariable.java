package jisd.fl.probe.info;

import jisd.fl.util.analyze.CodeElementName;

public class SuspiciousVariable { //ローカル変数の場合のみ
    private CodeElementName failedTest = new CodeElementName("ENPTY");
    private final CodeElementName locateMethod;
    private final String variableName;
    private final boolean isPrimitive;
    private final boolean isField;
    private final boolean isArray;
    private final int arrayNth;
    private final String actualValue;

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

        this.locateMethod = new CodeElementName(locateMethod);
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

        this.locateMethod = new CodeElementName(locateMethod);
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
            CodeElementName failedTest,
            String locateMethod,
            String variableName,
            String actualValue,
            boolean isPrimitive,
            boolean isField,
            int arrayNth) {

        this.failedTest = failedTest;
        this.locateMethod = new CodeElementName(locateMethod);
        this.variableName = variableName;
        this.isPrimitive = isPrimitive;
        this.isField = isField;
        this.arrayNth = arrayNth;
        this.isArray = true;
        this.actualValue = actualValue;
    }

    //配列でない場合
    public SuspiciousVariable(
            CodeElementName failedTest,
            String locateMethod,
            String variableName,
            String actualValue,
            boolean isPrimitive,
            boolean isField) {

        this.failedTest = failedTest;
        this.locateMethod = new CodeElementName(locateMethod);
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

    public CodeElementName getLocateMethodElement() {
        return locateMethod;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof SuspiciousVariable)) return false;
        SuspiciousVariable vi = (SuspiciousVariable) obj;

        return
                this.variableName.equals(vi.variableName) &&
                        this.isPrimitive == vi.isPrimitive &&
                        this.isField == vi.isField &&
                        this.arrayNth == vi.arrayNth &&
                        this.isArray == vi.isArray &&
                        this.actualValue.equals(vi.actualValue);
    }

    public String getActualValue() {
        return actualValue;
    }

    @Override
    public String toString() {
        return " [PROBE TARGET] " + getVariableName(true, true) + "\n" +
                "       [ACTUAL] " + getActualValue() + "\n" +
                "     [LOCATION] " + locateMethod;
    }

    public CodeElementName getFailedTest() {
        return failedTest;
    }
}
