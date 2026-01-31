package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.element.MethodElementName;

import java.util.*;

public class SuspiciousArgument extends SuspiciousExpression {
    /** 引数を与え実行しようとしているメソッド */
    public final MethodElementName invokeMethodName;
    /** 何番目の引数に与えられた expr かを指定 */
    public final int argIndex;

    /** invoke メソッドが文中の何番目の直接呼び出しか（1-based） */
    public final int invokeCallCount;

    /** 戻り値を収集すべき直接呼び出しの番号リスト（1-based） */
    public final List<Integer> collectAtCounts;

    public SuspiciousArgument(MethodElementName failedTest,
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
        super(failedTest, locateMethod, locateLine, actualValue, stmtString, hasMethodCalling, directNeighborVariableNames, indirectNeighborVariableNames);
        this.argIndex = argIndex;
        this.invokeMethodName = invokeMethodName;
        this.invokeCallCount = invokeCallCount;
        this.collectAtCounts = collectAtCounts;
    }

    @Override
    public String toString() {
        return "[ ARGUMENT ] ( " + locateMethod + " line:" + locateLine + " ) " + stmtString();
    }
}