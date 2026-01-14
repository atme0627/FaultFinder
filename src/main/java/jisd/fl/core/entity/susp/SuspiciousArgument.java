package jisd.fl.core.entity.susp;

import jisd.fl.core.entity.MethodElementName;

import java.util.*;

public class SuspiciousArgument extends SuspiciousExpression {
    //引数を与え実行しようとしているメソッド
    public final MethodElementName calleeMethodName;
    //何番目の引数に与えられたexprかを指定
    public final int argIndex;

    //対象の引数内の最初のmethodCallがstmtで何番目か
    public final int targetCallCount;

    //return位置を調べたいmethod一覧
    final List<String> targetMethodNames;

    public SuspiciousArgument(MethodElementName failedTest,
                              MethodElementName locateMethod,
                              int locateLine,
                              String actualValue,
                              MethodElementName calleeMethodName,
                              int argIndex,
                              String stmtString,
                              boolean hasMethodCalling,
                              List<String> directNeighborVariableNames,
                              List<String> indirectNeighborVariableNames,
                              List<String> targetMethodNames,
                              int targetCallCount
    ) {
        super(failedTest, locateMethod, locateLine, actualValue, stmtString, hasMethodCalling, directNeighborVariableNames, indirectNeighborVariableNames);
        this.argIndex = argIndex;
        this.calleeMethodName = calleeMethodName;
        this.targetCallCount = targetCallCount;
        this.targetMethodNames = targetMethodNames;
    }

    @Override
    public String toString() {
        return "[ SUSPICIOUS ARGUMENT ] ( " + locateMethod + " line:" + locateLine + " ) " + stmtString();
    }

    public List<String> targetMethodNames() {
        return targetMethodNames;
    }
}