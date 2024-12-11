package jisd.fl.probe.assertinfo;

public class VariableInfo {
    private final String locateClass;
    private final String variableName;
    private final String variableType;
    private final boolean isPrimitive;
    private final boolean isField;
    private final int arrayNth;
    private final VariableInfo targetField;

    public VariableInfo(String locateClass,
                        String variableName,
                        String variableType,
                        boolean isField,
                        int arrayNth,
                        VariableInfo targetField){

        this.locateClass = locateClass;
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

    @Override
    public String toString(){
        return this.variableName + ((this.targetField != null) ? "." + this.targetField.variableName : "");
    }
}
