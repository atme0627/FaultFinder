package probe;

import java.util.function.Predicate;

//変数と定数の比較のみ対応 ex.) sum >= 10
public class AssertTrueInfo extends AssertInfo {
    Predicate<Object> cond;

    public AssertTrueInfo(String variableName, String path, String testName, int line, Predicate<Object> cond) {
        super(AssertType.TRUE, variableName, path, testName, line);
        this.cond = cond;
    }

    public Boolean eval(Object variable){
        return cond.test(variable);
    }
}
