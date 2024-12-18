package jisd.fl.probe.assertinfo;

public abstract class FailedAssertInfo {
    private final AssertType assertType;
    private final String testClassName;
    private final String testMethodName;
    private final VariableInfo variableInfo;

    //testMethodNameはフルネーム、シグニチャあり
    public FailedAssertInfo(AssertType assertType,
                            String testMethodName,
                            VariableInfo variableInfo) {

        this.assertType = assertType;
        this.testClassName = testMethodName.split("#")[0];
        this.testMethodName = testMethodName;
        this.variableInfo = variableInfo;
    }

    public abstract Boolean eval(String variable);
    
    public AssertType getAssertType() {
        return assertType;
    }

    public String getTestClassName() {
        return testClassName;
    }

    public String getTestMethodName() {
        return  testMethodName;
    }

    public VariableInfo getVariableInfo() {
        return variableInfo;
    }
}
