package jisd.fl.probe.info;

import jisd.fl.core.entity.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousVariable;

import java.util.List;

public class SuspiciousAssignment extends SuspiciousExpression {

    //左辺で値が代入されている変数の情報
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
        super(failedTest, locateMethod, locateLine, assignTarget.getActualValue(), stmtString, hasMethodCalling, directNeighborVariableNames, indirectNeighborVariableNames);
        this.assignTarget = assignTarget;
    }

    @Override
    public String toString() {
        return "[ SUSPICIOUS ASSIGNMENT ] ( " + locateMethod + " line:" + locateLine + " ) " + stmtString();
    }
}