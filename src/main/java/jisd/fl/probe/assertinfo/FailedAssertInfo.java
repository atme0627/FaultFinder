package jisd.fl.probe.assertinfo;

//actual, expectedはStringで管理。比較もStringが一致するかどうかで判断。
//typeNameがプリミティブ型の場合、fieldNameはprimitiveに
public abstract class FailedAssertInfo {
    private final int arrayNth;
    private final AssertType assertType;
    private final String testClassName;
    private final String testMethodName;
    private final String variableName;
    private final String typeName;
    private final String fieldName;
    private boolean isPrimitive;

    public FailedAssertInfo(AssertType assertType,
                            String testClassName,
                            String testMethodName,
                            String variableName,
                            String typeName,
                            String fieldName,
                            int arrayNth) {

        this.assertType = assertType;
        this.testClassName = testClassName;
        this.testMethodName = testMethodName;
        this.variableName = variableName;
        this.typeName = typeName;
        this.fieldName = fieldName;
        this.arrayNth = arrayNth;

        setIsPrimitive();
    }

    public abstract Boolean eval(String variable);

    //typeNameがprimitive型かどうか
    private void setIsPrimitive(){
        switch (typeName){
            case "char":
            case "boolean":
            case "byte":
            case "short":
            case "int":
            case "float":
            case "long":
            case "double":
                isPrimitive = true;
                break;
            default:
                isPrimitive = false;
        }
    }

    public boolean isPrimitive(){
        return isPrimitive;
    }

    public AssertType getAssertType() {
        return assertType;
    }

    public String getVariableName() {
        return variableName;
    }

    public String getTestClassName() {
        return testClassName;
    }

    public String getTestMethodName() {
        return  testMethodName;
    }

    public String getTypeName() {
        return typeName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public boolean isArray(){
        return arrayNth != -1;
    }

    public int getArrayNth() {
        return arrayNth;
    }
}
