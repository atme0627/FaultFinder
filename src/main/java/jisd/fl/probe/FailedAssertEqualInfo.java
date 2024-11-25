package jisd.fl.probe;
//actual, expectedはStringで管理。比較もStringが一致するかどうかで判断。
public class FailedAssertEqualInfo extends FailedAssertInfo {
    private final String actual;
    public FailedAssertEqualInfo(String variableName, String actual, String srcDir, String binDir, String testClassName, String testMethodName, int line) {
        super(AssertType.EQUAL, variableName, srcDir, binDir,  testClassName, testMethodName, line);
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
