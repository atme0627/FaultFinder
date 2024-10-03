package probe;

public abstract class AssertInfo {
    final AssertType type;
    final String variableName;
    final String path;
    final String testName;
    final int line;

    public AssertInfo(AssertType type, String variableName, String path, String testName, int line) {
        this.type = type;
        this.variableName = variableName;
        this.path = path;
        this.testName = testName;
        this.line = line;
    }

    abstract Boolean eval(Object variable);
}
