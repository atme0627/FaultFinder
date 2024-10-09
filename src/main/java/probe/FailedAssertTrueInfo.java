package probe;

import java.util.function.Consumer;
import java.util.function.Predicate;

//変数と定数の比較のみ対応 ex.) sum >= 10
public class FailedAssertTrueInfo extends FailedAssertInfo<Integer> {
    private final Predicate<Integer> cond;

    public FailedAssertTrueInfo(String variableName, String path, String testName, int line, Predicate<Integer> cond) {
        super(AssertType.TRUE, variableName, path, testName, line);
        this.cond = cond;
    }

    public Boolean eval(Integer variable){
        return cond.test(variable);
    }
}
