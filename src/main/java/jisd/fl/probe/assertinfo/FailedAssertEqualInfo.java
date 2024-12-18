package jisd.fl.probe.assertinfo;

//actual, expectedはStringで管理。比較もStringが一致するかどうかで判断。
public class FailedAssertEqualInfo extends FailedAssertInfo {
    private final String actual;

    public FailedAssertEqualInfo(String testMethodName,
                                 String actual,
                                 VariableInfo variableInfo) {

        super(AssertType.EQUAL,
                testMethodName,
                variableInfo
        );
        this.actual = actual;
    }

    @Override
    public Boolean eval(String variable){
        return variable.equals(getActualValue());
    }

    public String getActualValue() {
        return actual;
    }
}
