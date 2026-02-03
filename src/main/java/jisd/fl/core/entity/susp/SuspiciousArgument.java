package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.LineElementName;
import jisd.fl.core.entity.element.MethodElementName;

import java.util.List;
import java.util.Objects;

/**
 * 引数式を表すクラス。
 * メソッド呼び出しの引数として渡された値を追跡する。
 */
public final class SuspiciousArgument implements SuspiciousExpression {

    private final MethodElementName failedTest;
    private final LineElementName location;
    private final String actualValue;
    private final String stmtString;
    private final boolean hasMethodCalling;
    private final List<String> directNeighborVariableNames;
    private final List<String> indirectNeighborVariableNames;

    /** 引数を与え実行しようとしているメソッド */
    public final MethodElementName invokeMethodName;

    /** 何番目の引数に与えられた expr かを指定 */
    public final int argIndex;

    /** invoke メソッドが文中の何番目の直接呼び出しか（1-based） */
    public final int invokeCallCount;

    /** 戻り値を収集すべき直接呼び出しの番号リスト（1-based） */
    public final List<Integer> collectAtCounts;

    public SuspiciousArgument(
            MethodElementName failedTest,
            MethodElementName locateMethod,
            int locateLine,
            String actualValue,
            MethodElementName invokeMethodName,
            int argIndex,
            String stmtString,
            boolean hasMethodCalling,
            List<String> directNeighborVariableNames,
            List<String> indirectNeighborVariableNames,
            List<Integer> collectAtCounts,
            int invokeCallCount
    ) {
        this.failedTest = Objects.requireNonNull(failedTest);
        this.location = new LineElementName(locateMethod, locateLine);
        this.actualValue = Objects.requireNonNull(actualValue);
        this.invokeMethodName = Objects.requireNonNull(invokeMethodName);
        this.argIndex = argIndex;
        this.stmtString = Objects.requireNonNull(stmtString);
        this.hasMethodCalling = hasMethodCalling;
        this.directNeighborVariableNames = List.copyOf(directNeighborVariableNames);
        this.indirectNeighborVariableNames = List.copyOf(indirectNeighborVariableNames);
        this.collectAtCounts = List.copyOf(collectAtCounts);
        this.invokeCallCount = invokeCallCount;
    }

    @Override public MethodElementName failedTest() { return failedTest; }
    @Override public LineElementName location() { return location; }
    @Override public String actualValue() { return actualValue; }
    @Override public String stmtString() { return stmtString; }
    @Override public boolean hasMethodCalling() { return hasMethodCalling; }
    @Override public List<String> directNeighborVariableNames() { return directNeighborVariableNames; }
    @Override public List<String> indirectNeighborVariableNames() { return indirectNeighborVariableNames; }

    public MethodElementName invokeMethodName() { return invokeMethodName; }
    public int argIndex() { return argIndex; }
    public int invokeCallCount() { return invokeCallCount; }
    public List<Integer> collectAtCounts() { return collectAtCounts; }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SuspiciousExpression se)) return false;
        return this.failedTest.equals(se.failedTest()) &&
                this.location.equals(se.location()) &&
                this.actualValue.equals(se.actualValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(failedTest, location, actualValue);
    }

    @Override
    public String toString() {
        return "[ ARGUMENT ] ( " + locateMethod() + " line:" + locateLine() + " ) " + stmtString();
    }
}