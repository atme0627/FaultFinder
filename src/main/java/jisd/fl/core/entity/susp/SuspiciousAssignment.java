package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.LineElementName;
import jisd.fl.core.entity.element.MethodElementName;

import java.util.List;
import java.util.Objects;

/**
 * 代入式を表すクラス。
 * 左辺で値が代入されている変数の情報を保持する。
 */
public final class SuspiciousAssignment implements SuspiciousExpression {

    private final MethodElementName failedTest;
    private final LineElementName location;
    private final String stmtString;
    private final boolean hasMethodCalling;
    private final List<String> directNeighborVariableNames;
    private final List<String> indirectNeighborVariableNames;

    /** 左辺で値が代入されている変数の情報 */
    public final SuspiciousVariable assignTarget;

    public SuspiciousAssignment(
            MethodElementName failedTest,
            MethodElementName locateMethod,
            int locateLine,
            SuspiciousVariable assignTarget,
            String stmtString,
            boolean hasMethodCalling,
            List<String> directNeighborVariableNames,
            List<String> indirectNeighborVariableNames
    ) {
        this.failedTest = Objects.requireNonNull(failedTest);
        this.location = new LineElementName(locateMethod, locateLine);
        this.assignTarget = Objects.requireNonNull(assignTarget);
        this.stmtString = Objects.requireNonNull(stmtString);
        this.hasMethodCalling = hasMethodCalling;
        this.directNeighborVariableNames = List.copyOf(directNeighborVariableNames);
        this.indirectNeighborVariableNames = List.copyOf(indirectNeighborVariableNames);
    }

    @Override public MethodElementName failedTest() { return failedTest; }
    @Override public LineElementName location() { return location; }
    @Override public String actualValue() { return assignTarget.actualValue(); }
    @Override public String stmtString() { return stmtString; }
    @Override public boolean hasMethodCalling() { return hasMethodCalling; }
    @Override public List<String> directNeighborVariableNames() { return directNeighborVariableNames; }
    @Override public List<String> indirectNeighborVariableNames() { return indirectNeighborVariableNames; }

    public SuspiciousVariable assignTarget() { return assignTarget; }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SuspiciousExpression se)) return false;
        return this.failedTest.equals(se.failedTest()) &&
                this.location.equals(se.location()) &&
                this.actualValue().equals(se.actualValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(failedTest, location, actualValue());
    }

    @Override
    public String toString() {
        return "[  ASSIGN  ] ( " + locateMethod() + " line:" + locateLine() + " ) " + stmtString();
    }
}