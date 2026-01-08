package jisd.fl.infra.javaparser;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.probe.info.*;

import java.util.List;
import java.util.stream.Collectors;


public class JavaParserSuspiciousExpressionFactory implements SuspiciousExpressionFactory {

    @Override
    public SuspiciousAssignment createAssignment(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, SuspiciousVariable assignTarget) {
        Statement stmt = TmpJavaParserUtils.extractStmt(locateMethod, locateLine);
        Expression expr = JavaParserSuspAssign.extractExprAssign(true, stmt);
        boolean hasMethodCalling = !expr.findAll(MethodCallExpr.class).isEmpty();

        List<String> directNeighborVariableNames = extractDirectNeighborVariableNames(expr);
        List<String> indirectNeighborVariableNames = extractIndirectNeighborVariableNames(expr);
        return new SuspiciousAssignment(
                failedTest,
                locateMethod,
                locateLine,
                assignTarget,
                stmt.toString(),
                hasMethodCalling,
                directNeighborVariableNames,
                indirectNeighborVariableNames
        );
    }

    @Override
    public SuspiciousReturnValue createReturnValue(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue) {
        Statement stmt = TmpJavaParserUtils.extractStmt(locateMethod, locateLine);
        Expression expr = JavaParserSuspReturn.extractExprReturnValue(stmt);
        boolean hasMethodCalling = !expr.findAll(MethodCallExpr.class).isEmpty();

        List<String> directNeighborVariableNames = extractDirectNeighborVariableNames(expr);
        List<String> indirectNeighborVariableNames = extractIndirectNeighborVariableNames(expr);
        return new SuspiciousReturnValue(
                failedTest,
                locateMethod,
                locateLine,
                actualValue,
                stmt.toString(),
                hasMethodCalling,
                directNeighborVariableNames,
                indirectNeighborVariableNames
        );
    }

    @Override
    //callCountAfterTargetInLineその行の中で呼び出し元のメソッドの後に何回他のメソッドが呼ばれるか
    public SuspiciousArgument createArgument(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue, MethodElementName calleeMethodName, int argIndex, int callCountAfterTargetInLine) {
        Statement stmt = TmpJavaParserUtils.extractStmt(locateMethod, locateLine);
        String stmtString = createArgStmtString(stmt, callCountAfterTargetInLine, argIndex, calleeMethodName);
        Expression expr = JavaParserSuspArg.extractExprArg(true, stmt, callCountAfterTargetInLine, argIndex, calleeMethodName);
        boolean hasMethodCalling = !expr.findAll(MethodCallExpr.class).isEmpty();

        List<String> directNeighborVariableNames = extractDirectNeighborVariableNames(expr);
        List<String> indirectNeighborVariableNames = extractIndirectNeighborVariableNames(expr);
        List<String> targetMethodName = extractArgTargetMethodNames(expr);
        int targetCallCount = JavaParserSuspArg.getCallCountBeforeTargetArgEval(stmt, callCountAfterTargetInLine, argIndex, calleeMethodName);
        return new SuspiciousArgument(
                failedTest,
                locateMethod,
                locateLine,
                actualValue,
                calleeMethodName,
                argIndex,
                stmtString,
                hasMethodCalling,
                directNeighborVariableNames,
                indirectNeighborVariableNames,
                targetMethodName,
                targetCallCount
        );
    }


    private static String createArgStmtString(Statement stmt, int callCountAfterTargetInLine, int argIndex, MethodElementName calleeMethodName) {
        final String BG_GREEN = "\u001B[42m";
        final String RESET = "\u001B[0m";
        LexicalPreservingPrinter.setup(stmt);
        JavaParserSuspArg.extractExprArg(false, stmt, callCountAfterTargetInLine, argIndex, calleeMethodName).getTokenRange().ifPresent(tokenRange -> {
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

    /**
     * exprから次に探索の対象となる変数の名前を取得する。
     * exprの演算に直接用いられている変数が対象。
     */
    public List<String> extractDirectNeighborVariableNames(Expression expr){
        return expr.findAll(NameExpr.class).stream()
                //引数やメソッド呼び出しに用いられる変数を除外
                .filter(nameExpr -> nameExpr.findAncestor(MethodCallExpr.class).isEmpty())
                .map(NameExpr::toString)
                .collect(Collectors.toList());
    }

    /**
     * exprから次に探索の対象となる変数の名前を取得する。
     * expr内で間接的に使用されている変数が対象。
     */
    public List<String> extractIndirectNeighborVariableNames(Expression expr){
        return expr.findAll(NameExpr.class).stream()
                //引数やメソッド呼び出しに用いられる変数を除外
                .filter(nameExpr -> !nameExpr.findAncestor(MethodCallExpr.class).isEmpty())
                .map(NameExpr::toString)
                .collect(Collectors.toList());
    }

    public List<String> extractArgTargetMethodNames(Expression expr){
        return expr.findAll(MethodCallExpr.class)
                .stream()
                .filter(mce -> mce.findAncestor(MethodCallExpr.class).isEmpty())
                .map(mce -> mce.getName().toString())
                .collect(Collectors.toList());
    }
}
