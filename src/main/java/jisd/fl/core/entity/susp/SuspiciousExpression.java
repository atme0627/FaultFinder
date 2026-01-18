package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.MethodElementName;

import java.util.*;

public abstract class SuspiciousExpression {
    //どのテスト実行時の話かを指定
    public final MethodElementName failedTest;
    //フィールドの場合は<ulinit>で良い
    public final MethodElementName locateMethod;
    public final int locateLine;
    public final String actualValue;
    private final String stmtString;
    public final boolean hasMethodCalling;
    public final List<String> directNeighborVariableNames;
    public final List<String> indirectNeighborVariableNames;


    SuspiciousExpression(
            MethodElementName failedTest,
            MethodElementName locateMethod,
            int locateLine,
            String actualValue,
            String stmtString,
            boolean hasMethodCalling,
            List<String> directNeighborVariableNames,
            List<String> indirectNeighborVariableNames
    ) {
        this.failedTest = failedTest;
        this.locateMethod = locateMethod;
        this.locateLine = locateLine;
        this.actualValue = actualValue;
        this.stmtString = stmtString;
        this.hasMethodCalling = hasMethodCalling;
        this.directNeighborVariableNames = directNeighborVariableNames;
        this.indirectNeighborVariableNames = indirectNeighborVariableNames;

    }

    @Override
    public boolean equals(Object obj){
        if(!(obj instanceof SuspiciousExpression se)) return false;
        return this.failedTest.equals(se.failedTest) &&
                this.locateMethod.equals(se.locateMethod) &&
                this.locateLine == se.locateLine &&
                this.actualValue.equals(se.actualValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(failedTest, locateMethod, locateLine, actualValue);
    }

    public String stmtString(){
        return stmtString;
    }

    public boolean hasMethodCalling(){
        return hasMethodCalling;
    }
}
