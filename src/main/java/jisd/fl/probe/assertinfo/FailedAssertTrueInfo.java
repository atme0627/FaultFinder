package jisd.fl.probe.assertinfo;

//変数と定数の比較のみ対応 ex.) sum >= 10
public class FailedAssertTrueInfo extends FailedAssertInfo {

    public FailedAssertTrueInfo(AssertType assertType, String variableName, String srcDir, String binDir, String testClassName, String testMethodName, int line) {
        super(assertType, variableName, srcDir, binDir, testClassName, testMethodName, line);
    }

    @Override
    public Boolean eval(String variable) {
        return null;
    }
}
