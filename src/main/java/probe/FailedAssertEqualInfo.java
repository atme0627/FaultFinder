package probe;
//actual, expectedはStringで管理。比較もStringが一致するかどうかで判断。
public class FailedAssertEqualInfo extends FailedAssertInfo {
    private final String expected;
    private final String actual;
    public FailedAssertEqualInfo(String variableName, String expected, String actual, String srcDir, String binDir, String testClassName, String testMethodName, int line) {
        super(AssertType.EQUAL, variableName, srcDir, binDir,  testClassName, testMethodName, line);
        this.expected = expected;
        this.actual = actual;
    }

    @Override
    public Boolean eval(String variable){
        return variable.equals(getActualValue());
    }

    public String getExpectedValue() {
        return expected;
    }

    public String getActualValue() {
        return actual;
    }
}
