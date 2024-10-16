package probe;

public class FailedAssertEqualInfo extends FailedAssertInfo<Number> {
    private final Number expected;
    private final Number actual;
    public FailedAssertEqualInfo(String variableName, Number expected, Number actual, String srcDir, String binDir, String testClassName, String testMethodName, int line) {
        super(AssertType.EQUAL, variableName, srcDir, binDir,  testClassName, testMethodName, line);
        this.expected = expected;
        this.actual = actual;
    }

    public Boolean eval(Number variable){
        return variable.equals(getActualValue());
    }

    public Number getExpectedValue() {
        return expected;
    }

    public Number getActualValue() {
        return actual;
    }
}
