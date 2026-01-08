package jisd.fl.probe.info;

import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.core.entity.MethodElementName;

import java.util.*;
import java.util.stream.Collectors;

public class SuspiciousArgument extends SuspiciousExpression {
    //引数を与え実行しようとしているメソッド
    final MethodElementName calleeMethodName;
    //何番目の引数に与えられたexprかを指定
    final int argIndex;
    //その行の中で呼び出し元のメソッドの後に何回他のメソッドが呼ばれるか
    final int CallCountAfterTargetInLine;

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
                              int CallCountAfterTargetInLine,
                              String stmtString,
                              boolean hasMethodCalling,
                              List<String> directNeighborVariableNames,
                              List<String> indirectNeighborVariableNames
    ) {
        super(failedTest, locateMethod, locateLine, actualValue, stmtString, hasMethodCalling, directNeighborVariableNames, indirectNeighborVariableNames);
        this.argIndex = argIndex;
        this.calleeMethodName = calleeMethodName;
        this.CallCountAfterTargetInLine = CallCountAfterTargetInLine;

        Statement stmt = TmpJavaParserUtils.extractStmt(this.locateMethod, this.locateLine);
        this.expr = JavaParserSuspArg.extractExprArg(true, stmt, this.CallCountAfterTargetInLine, this.argIndex, this.calleeMethodName);
        this.targetCallCount = JavaParserSuspArg.getCallCountBeforeTargetArgEval(stmt, this.CallCountAfterTargetInLine, this.argIndex, this.calleeMethodName);

        this.targetMethodName = this.expr.findAll(MethodCallExpr.class)
                .stream()
                .filter(mce -> mce.findAncestor(MethodCallExpr.class).isEmpty())
                .map(mce -> mce.getName().toString())
                .collect(Collectors.toList());
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