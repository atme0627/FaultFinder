package jisd.fl.probe.assertinfo;

import jisd.fl.probe.info.SuspiciousVariable;

@Deprecated
public abstract class FailedAssertInfo {
    private final AssertType assertType;
    private final String testClassName;
    private final String testMethodName;
    private final SuspiciousVariable suspiciousVariable;

    //testMethodNameはフルネーム、シグニチャあり
    public FailedAssertInfo(AssertType assertType,
                            String testMethodName,
                            SuspiciousVariable suspiciousVariable) {

        this.assertType = assertType;
        this.testClassName = testMethodName.split("#")[0];
        this.testMethodName = testMethodName;
        this.suspiciousVariable = suspiciousVariable;
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

    public SuspiciousVariable getVariableInfo() {
        return suspiciousVariable;
    }
}
