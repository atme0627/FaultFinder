package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.MethodElementName;

import java.util.List;

public class SuspiciousAssignment extends SuspiciousExpression {

    //左辺で値が代入されている変数の情報
    public final SuspiciousLocalVariable assignTarget;

    public SuspiciousAssignment(
            MethodElementName failedTest,
            MethodElementName locateMethod,
            int locateLine,
            SuspiciousLocalVariable assignTarget,
            String stmtString,
            boolean hasMethodCalling,
            List<String> directNeighborVariableNames,
            List<String> indirectNeighborVariableNames
    ) {
        super(failedTest, locateMethod, locateLine, assignTarget.actualValue(), stmtString, hasMethodCalling, directNeighborVariableNames, indirectNeighborVariableNames);
        this.assignTarget = assignTarget;
    }

    @Override
    public String toString() {
        return "[  ASSIGN  ] ( " + locateMethod + " line:" + locateLine + " ) " + stmtString();
    }
}