package jisd.fl.probe.assertinfo;

public class VariableInfo {
    private final String locateClass;
    private final String locateMethod; //ローカル変数の場合のみ
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

        this.locateClass = locateMethod.split("#")[0];
        this.locateMethod = locateMethod;

        this.variableName = variableName;
        this.isPrimitive = isPrimitive;
        this.isField = isField;
        this.arrayNth = arrayNth;
        this.isArray = isArray;
        this.targetField = targetField;
        this.actualValue = actualValue;
    }

    public String getLocateClass() {
        return locateClass;
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

    public String getLocateMethod(){
        return getLocateMethod(false);
    }

    public String getLocateMethod(boolean withClass) {
        if(withClass){
            return locateMethod;
        }
        else {
            if(locateMethod.contains("#")) {
                return locateMethod.split("#")[1];
            } else {
                return locateMethod;
            }
        }
    }

    @Override
    public String toString(){
        return this.variableName + ((this.targetField != null) ? "." + this.targetField.variableName : "");
    }

    public String getActualValue() {
        return actualValue;
    }
}
