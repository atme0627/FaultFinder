package probe;

public class FailedAssertEqualInfo extends FailedAssertInfo<Object> {
    private final Object expected;
    private final Object actual;
    public FailedAssertEqualInfo(String variableName, Object expected, Object actual, String path, String testName, int line) {
        super(AssertType.EQUAL, variableName, path, testName, line);
        this.expected = expected;
        this.actual = actual;
    }

    public Boolean eval(Object variable){
        return variable.equals(getActualValue());
    }

    public Object getExpectedValue() {
        return expected;
    }

    public Object getActualValue() {
        return actual;
    }
}
