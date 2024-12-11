package jisd.fl.probe.assertinfo;

//actual, expectedはStringで管理。比較もStringが一致するかどうかで判断。
//arrayNthはfieldが配列の場合は対象の配列の場所を、配列でない場合は-1を指定
public class FailedAssertEqualInfo extends FailedAssertInfo {
    private final String actual;

    public FailedAssertEqualInfo(String testClassName,
                                 String testMethodName,
                                 String actual,
                                 VariableInfo variableInfo) {

        super(AssertType.EQUAL,
                testClassName,
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
