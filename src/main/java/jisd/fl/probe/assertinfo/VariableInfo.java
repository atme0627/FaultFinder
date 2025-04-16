package jisd.fl.probe.assertinfo;

import jisd.fl.util.analyze.CodeElement;

public class VariableInfo { //ローカル変数の場合のみ
    private final CodeElement locateMethod;
    private final String variableName;
    private final boolean isPrimitive;
    private final boolean isField;
    private final boolean isArray;
    private final int arrayNth;
    private final String actualValue;
    private final VariableInfo targetField;

    //locateはローカル変数の場合はメソッド名まで(フルネーム、シグニチャあり)
    //フィールドの場合はクラス名まで
    public VariableInfo(String locateMethod,
                        String variableName,
                        boolean isPrimitive,
                        boolean isField,
                        boolean isArray,
                        int arrayNth,
                        String actualValue,
                        VariableInfo targetField){

        this.locateMethod = new CodeElement(locateMethod);
        this.variableName = variableName;
        this.isPrimitive = isPrimitive;
        this.isField = isField;
        this.arrayNth = arrayNth;
        this.isArray = isArray;
        this.targetField = targetField;
        this.actualValue = actualValue;
    }

    public String getLocateClass() {
        return locateMethod.getFullyQualifiedClassName();
    }

    public String getVariableName(){
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

    public VariableInfo getTargetField() {
        return targetField;
    }

    public String getLocateMethod(boolean withClass) {
        if(withClass){
            return locateMethod.getFullyQualifiedMethodName();
        }
        else {
            return locateMethod.methodSignature;
        }
    }

    public CodeElement getLocateMethodElement(){
        return locateMethod;
    }

    @Override
    public String toString(){
        return this.variableName + ((this.targetField != null) ? "." + this.targetField.variableName : "");
    }

    @Override
    public boolean equals(Object obj){
        if(obj == null) return false;
        if(!(obj instanceof VariableInfo)) return false;
        VariableInfo vi = (VariableInfo) obj;

        return
            this.variableName.equals(vi.variableName) &&
            this.isPrimitive == vi.isPrimitive &&
            this.isField == vi.isField &&
            this.arrayNth == vi.arrayNth &&
            this.isArray == vi.isArray &&
            ((this.targetField == null && vi.targetField == null) || this.targetField.equals(vi.targetField)) &&
            this.actualValue.equals(vi.actualValue);
    }

    public String getActualValue() {
        return actualValue;
    }
}
