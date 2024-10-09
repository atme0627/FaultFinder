package probe;

public abstract class FailedAssertInfo<T> {
    private final String variableName;
    private final AssertType assertType;
    private final String path;
    private final String testName;
    private final int line;

    public FailedAssertInfo(AssertType assertType, String variableName, String path, String testName, int line) {
        this.variableName = variableName;
        this.assertType = assertType;
        this.path = path;
        this.testName = testName;
        this.line = line;
    }

    abstract Boolean eval(T variable);


    public AssertType getAssertType() {
        return assertType;
    }

    public String getVariableName() {
        return variableName;
    }

    public String getPath() {
        return path;
    }

    public String getTestName() {
        return testName;
    }

    public int getLineOfAssert() {
        return line;
    }

}
