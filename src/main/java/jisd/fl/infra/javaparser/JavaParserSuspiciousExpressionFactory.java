package jisd.fl.infra.javaparser;

import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.probe.info.*;

public class JavaParserSuspiciousExpressionFactory implements SuspiciousExpressionFactory {

    @Override
    public SuspiciousAssignment createAssignment(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, SuspiciousVariable assignTarget) {
        Statement stmt = TmpJavaParserUtils.extractStmt(locateMethod, locateLine);
        return new SuspiciousAssignment(failedTest, locateMethod, locateLine, assignTarget, stmt.toString());
    }

    @Override
    public SuspiciousReturnValue createReturnValue(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue) {
        Statement stmt = TmpJavaParserUtils.extractStmt(locateMethod, locateLine);
        return new SuspiciousReturnValue(failedTest, locateMethod, locateLine, actualValue, stmt.toString());
    }

    @Override
    public SuspiciousArgument createArgument(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue, MethodElementName calleeMethodName, int argIndex, int callCountAfterTargetInLine) {
        Statement stmt = TmpJavaParserUtils.extractStmt(locateMethod, locateLine);
        String stmtString = createArgStmtString(stmt, callCountAfterTargetInLine, argIndex, calleeMethodName);
        return new SuspiciousArgument(failedTest, locateMethod, locateLine, actualValue, calleeMethodName, argIndex, callCountAfterTargetInLine, stmtString);
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
}
