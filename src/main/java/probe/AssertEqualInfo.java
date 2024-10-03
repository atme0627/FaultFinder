package probe;

public class AssertEqualInfo extends AssertInfo {

    final Object expected;

    public AssertEqualInfo(String variableName, String path, String testName, int line, Object expected) {
        super(AssertType.EQUAL, variableName, path, testName, line);
        this.expected = expected;
    }

    public Boolean eval(Object variable){
        return variable.equals(expected);
    }
}
