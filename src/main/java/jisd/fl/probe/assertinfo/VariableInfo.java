package jisd.fl.probe.assertinfo;

public class VariableInfo implements Cloneable{
    private final String locateClass;
    private final String locateMethod; //ローカル変数の場合のみ
    private final String variableName;
    private final String variableType;
    private final boolean isPrimitive;
    private final boolean isField;
    private final int arrayNth;
    private final VariableInfo targetField;

    //locateはローカル変数の場合はメソッド名まで(フルネーム、シグニチャあり)
    //フィールドの場合はクラス名まで
    public VariableInfo(String locate,
                        String variableName,
                        String variableType,
                        boolean isField,
                        int arrayNth,
                        VariableInfo targetField){

        if(isField) {
            this.locateClass = locate;
            this.locateMethod = null;
        }
        else {
            this.locateClass = locate.split("#")[0];
            this.locateMethod = locate;
        }

        this.variableName = variableName;
        this.variableType = variableType;
        this.isField = isField;
        this.arrayNth = arrayNth;
        this.targetField = targetField;

        switch (variableType){
            case "char":
            case "boolean":
            case "byte":
            case "short":
            case "int":
            case "float":
            case "long":
            case "double":
                this.isPrimitive = true;
                break;
            default:
                this.isPrimitive = false;
        }
    }

    public String getLocateClass() {
        return locateClass;
    }

    public String getVariableName(){
        return getVariableName(false);
    }

    public String getVariableName(boolean withThis) {
        return (isField() && withThis ? "this." : "") + variableName;
    }

    public String getVariableType() {
        return variableType;
    }

    public boolean isArray() {
        return variableType.endsWith("[]");
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
            return locateMethod.split("#")[1];
        }
    }

    @Override
    public String toString(){
        return this.variableName + ((this.targetField != null) ? "." + this.targetField.variableName : "");
    }
}
