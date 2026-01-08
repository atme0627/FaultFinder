package jisd.fl.probe.info;

import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.core.entity.MethodElementName;

import java.util.*;

public class SuspiciousArgument extends SuspiciousExpression {
    //引数を与え実行しようとしているメソッド
    final MethodElementName calleeMethodName;
    //何番目の引数に与えられたexprかを指定
    final int argIndex;

    //対象の引数内の最初のmethodCallがstmtで何番目か
    final int targetCallCount;

    //return位置を調べたいmethod一覧
    final List<String> targetMethodName;

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
                              List<String> targetMethodName,
                              int targetCallCount
    ) {
        super(failedTest, locateMethod, locateLine, actualValue, stmtString, hasMethodCalling, directNeighborVariableNames, indirectNeighborVariableNames);
        this.argIndex = argIndex;
        this.calleeMethodName = calleeMethodName;
        this.targetCallCount = targetCallCount;
        this.targetMethodName = targetMethodName;
    }

    @Override
    //引数のindexを指定してその引数の評価の直前でsuspendするのは激ムズなのでやらない
    //引数を区別せず、引数の評価の際に呼ばれたすべてのメソッドについて情報を取得し
    //Expressionを静的解析してexpressionで直接呼ばれてるメソッドのみに絞る
    //ex.) expressionがx.f(y.g())の時、fのみとる。y.g()はfの探索の後行われるはず
    public List<SuspiciousReturnValue> searchSuspiciousReturns() throws NoSuchElementException {
        return JDISuspArg.searchSuspiciousReturns(this);
    }

    /**
     * ある変数がその値を取る原因が呼び出し元の引数のあると判明した場合に使用
     */
    static public Optional<SuspiciousArgument> searchSuspiciousArgument(MethodElementName calleeMethodName, SuspiciousVariable suspVar) {
        return JDISuspArg.searchSuspiciousArgument(calleeMethodName, suspVar);
    }

    @Override
    public String toString() {
        return "[ SUSPICIOUS ARGUMENT ] ( " + locateMethod + " line:" + locateLine + " ) " + stmtString();
    }

    //引数の静的解析により、return位置を調べたいmethod一覧を取得する
    List<String> targetMethodName() {
        return targetMethodName;
    }
}