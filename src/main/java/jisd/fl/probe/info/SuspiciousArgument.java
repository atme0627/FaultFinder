package jisd.fl.probe.info;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.core.entity.MethodElementName;

import java.util.*;

public class SuspiciousArgument extends SuspiciousExpression {
    //引数を与え実行しようとしているメソッド
    final MethodElementName calleeMethodName;
    //何番目の引数に与えられたexprかを指定
    final int argIndex;
    //その行の中で呼び出し元のメソッドの後に何回他のメソッドが呼ばれるか
    final int CallCountAfterTargetInLine;
    final String stmtString;

    protected SuspiciousArgument(MethodElementName failedTest,
                                 MethodElementName locateMethod,
                                 int locateLine,
                                 String actualValue,
                                 MethodElementName calleeMethodName,
                                 int argIndex,
                                 int CallCountAfterTargetInLine) {
        super(failedTest, locateMethod, locateLine, actualValue);
        this.argIndex = argIndex;
        this.calleeMethodName = calleeMethodName;
        this.CallCountAfterTargetInLine = CallCountAfterTargetInLine;
        this.expr = extractExprArg(true, stmt, this.CallCountAfterTargetInLine, this.argIndex, this.calleeMethodName);

        this.stmtString = createStmtString(stmt, this.CallCountAfterTargetInLine, this.argIndex, this.calleeMethodName);

    }

    @Override
    //引数のindexを指定してその引数の評価の直前でsuspendするのは激ムズなのでやらない
    //引数を区別せず、引数の評価の際に呼ばれたすべてのメソッドについて情報を取得し
    //Expressionを静的解析してexpressionで直接呼ばれてるメソッドのみに絞る
    //ex.) expressionがx.f(y.g())の時、fのみとる。y.g()はfの探索の後行われるはず
    public List<SuspiciousReturnValue> searchSuspiciousReturns() throws NoSuchElementException{
        return JDISuspArg.searchSuspiciousReturns(this);
    }


    static protected Expression extractExprArg(boolean deleteParentNode, Statement stmt, int callCountAfterTargetInLine, int argIndex, MethodElementName calleeMethodName) {
        return JavaParserSuspArg.extractExprArg(deleteParentNode, stmt, callCountAfterTargetInLine, argIndex, calleeMethodName);
    }

    /**
     * ある変数がその値を取る原因が呼び出し元の引数のあると判明した場合に使用
     */
    static public Optional<SuspiciousArgument> searchSuspiciousArgument(MethodElementName calleeMethodName, SuspiciousVariable suspVar){
        return JDISuspArg.searchSuspiciousArgument(calleeMethodName, suspVar);
    }

    @Override
    public String toString(){
        return "[ SUSPICIOUS ARGUMENT ] ( " + locateMethod + " line:" + locateLine + " ) " + stmtString();
    }


    private static String createStmtString(Statement stmt, int callCountAfterTargetInLine, int argIndex, MethodElementName calleeMethodName) {
        final String BG_GREEN = "\u001B[42m";
        final String RESET    = "\u001B[0m";
        LexicalPreservingPrinter.setup(stmt);
        extractExprArg(false, stmt, callCountAfterTargetInLine, argIndex, calleeMethodName).getTokenRange().ifPresent(tokenRange -> {
                    // 子ノードに属するすべてのトークンに色付け
                    tokenRange.forEach(token -> {
                        String original = token.getText();
                        // ANSI エスケープシーケンスで背景黄色
                        token.setText(BG_GREEN + original + RESET);
                    });
                }
        );
        return LexicalPreservingPrinter.print(stmt);
    }

    @Override
    public String stmtString(){
        return stmtString;
    }

}
