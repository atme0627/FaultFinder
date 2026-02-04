package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.LineElementName;
import jisd.fl.core.entity.element.MethodElementName;

import java.util.List;
import java.util.Objects;

/**
 * 戻り値式を表すクラス。
 */
public final class SuspiciousReturnValue implements SuspiciousExpression {

    private final MethodElementName failedTest;
    private final LineElementName location;
    private final String actualValue;
    private final String stmtString;
    private final boolean hasMethodCalling;
    private final List<String> directNeighborVariableNames;
    private final List<String> indirectNeighborVariableNames;

    /**
     * 戻り値を収集すべきメソッド呼び出しの評価順位置リスト（1-based）。
     * 文中の全メソッド呼び出しを Java 評価順に並べたとき、
     * このリストに含まれる位置の呼び出しの戻り値のみを収集する。
     */
    public final List<Integer> targetReturnCallPositions;

    public SuspiciousReturnValue(
            MethodElementName failedTest,
            MethodElementName locateMethod,
            int locateLine,
            String actualValue,
            String stmtString,
            boolean hasMethodCalling,
            List<String> directNeighborVariableNames,
            List<String> indirectNeighborVariableNames,
            List<Integer> targetReturnCallPositions
    ) {
        this.failedTest = Objects.requireNonNull(failedTest);
        this.location = new LineElementName(locateMethod, locateLine);
        this.actualValue = Objects.requireNonNull(actualValue);
        this.stmtString = Objects.requireNonNull(stmtString);
        this.hasMethodCalling = hasMethodCalling;
        this.directNeighborVariableNames = List.copyOf(directNeighborVariableNames);
        this.indirectNeighborVariableNames = List.copyOf(indirectNeighborVariableNames);
        this.targetReturnCallPositions = List.copyOf(targetReturnCallPositions);
    }

    @Override public MethodElementName failedTest() { return failedTest; }
    @Override public LineElementName location() { return location; }
    @Override public String actualValue() { return actualValue; }
    @Override public String stmtString() { return stmtString; }
    @Override public boolean hasMethodCalling() { return hasMethodCalling; }
    @Override public List<String> directNeighborVariableNames() { return directNeighborVariableNames; }
    @Override public List<String> indirectNeighborVariableNames() { return indirectNeighborVariableNames; }

    public List<Integer> targetReturnCallPositions() { return targetReturnCallPositions; }

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
        return "[  RETURN  ] ( " + locateMethod() + " line:" + locateLine() + " ) " + stmtString();
    }
}