package probe;

public abstract class FailedAssertInfo<T> {
    private final String variableName;
    private final AssertType assertType;
    private final String srcDir;
    private final String binDir;
    private final String testClassName;
    private final String testMethodName;
    private final int line;

    public FailedAssertInfo(AssertType assertType, String variableName, String srcDir, String binDir, String testClassName, String testMethodName, int line) {
        this.variableName = variableName;
        this.assertType = assertType;
        this.srcDir = srcDir;
        this.binDir = binDir;
        this.testClassName = testClassName;
        this.testMethodName = testMethodName;
        this.line = line;
    }

    abstract Boolean eval(T variable);


    public AssertType getAssertType() {
        return assertType;
    }

    public String getVariableName() {
        return variableName;
    }

    public String getSrcDir() {
        return srcDir;
    }

    public String getBinDir() { return  binDir; }

    public String getTestClassName() {
        return testClassName;
    }

    public String getTestMethodName() { return  testMethodName; }

    public int getLineOfAssert() {
        return line;
    }

}
