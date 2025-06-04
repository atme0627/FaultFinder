package jisd.fl.probe.assertinfo;

import jisd.fl.probe.info.SuspiciousVariable;

//actual, expectedはStringで管理。比較もStringが一致するかどうかで判断。
public class FailedAssertEqualInfo extends FailedAssertInfo {
    private final String actual;

    public FailedAssertEqualInfo(String testMethodName,
                                 String actual,
                                 SuspiciousVariable suspiciousVariable) {


        super(AssertType.EQUAL,
                testMethodName,
                suspiciousVariable
        );
        this.actual = actual;
    }

    public FailedAssertEqualInfo(String testMethodName,
                                 SuspiciousVariable suspiciousVariable) {


        super(AssertType.EQUAL,
                testMethodName,
                suspiciousVariable
        );
        this.actual = "";
    }

    @Override
    public Boolean eval(String variable){
        return variable.equals(getActualValue());
    }

    public String getActualValue() {
        return actual;
    }
}
