package jisd.fl.infra.javaparser;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.github.javaparser.printer.configuration.DefaultPrinterConfiguration.ConfigOption.PRINT_COMMENTS;


public class JavaParserSuspiciousExpressionFactory implements SuspiciousExpressionFactory {

    @Override
    public SuspiciousAssignment createAssignment(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, SuspiciousVariable assignTarget) {
        Statement stmt = JavaParserUtils.extractStmt(locateMethod.classElementName, locateLine);
        Expression expr = JavaParserExpressionExtractor.extractExprAssign(true, stmt);
        boolean hasMethodCalling = !expr.findAll(MethodCallExpr.class).isEmpty();

        List<String> directNeighborVariableNames = extractDirectNeighborVariableNames(expr);
        List<String> indirectNeighborVariableNames = extractIndirectNeighborVariableNames(expr);
        return new SuspiciousAssignment(
                failedTest,
                locateMethod,
                locateLine,
                assignTarget,
                stmtToStringNoComments(stmt),
                hasMethodCalling,
                directNeighborVariableNames,
                indirectNeighborVariableNames
        );
    }

    @Override
    public SuspiciousReturnValue createReturnValue(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue) {
        Statement stmt = JavaParserUtils.extractStmt(locateMethod.classElementName, locateLine);
        Expression expr = JavaParserExpressionExtractor.extractExprReturnValue(stmt);
        boolean hasMethodCalling = !expr.findAll(MethodCallExpr.class).isEmpty();

        List<String> directNeighborVariableNames = extractDirectNeighborVariableNames(expr);
        List<String> indirectNeighborVariableNames = extractIndirectNeighborVariableNames(expr);
        return new SuspiciousReturnValue(
                failedTest,
                locateMethod,
                locateLine,
                actualValue,
                stmtToStringNoComments(stmt),
                hasMethodCalling,
                directNeighborVariableNames,
                indirectNeighborVariableNames
        );
    }

    @Override
    //callCountAfterTargetInLineその行の中で呼び出し元のメソッドの後に何回他のメソッドが呼ばれるか
    public SuspiciousArgument createArgument(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue, MethodElementName invokeMethodName, int argIndex, int callCountAfterTargetInLine) {
        Statement stmt = JavaParserUtils.extractStmt(locateMethod.classElementName, locateLine);
        String stmtString = createArgStmtString(stmt, callCountAfterTargetInLine, argIndex, invokeMethodName);
        Expression expr = JavaParserExpressionExtractor.extractExprArg(true, stmt, callCountAfterTargetInLine, argIndex, invokeMethodName);
        boolean hasMethodCalling = !expr.findAll(MethodCallExpr.class).isEmpty();

        List<String> directNeighborVariableNames = extractDirectNeighborVariableNames(expr);
        List<String> indirectNeighborVariableNames = extractIndirectNeighborVariableNames(expr);

        List<Expression> evalOrder = getEvalOrder(stmt);
        List<Integer> collectAtCounts = getCollectAtCounts(evalOrder, expr);
        int invokeCallCount = getInvokeCallCount(evalOrder, stmt, callCountAfterTargetInLine, argIndex, invokeMethodName);
        return new SuspiciousArgument(
                failedTest,
                locateMethod,
                locateLine,
                actualValue,
                invokeMethodName,
                argIndex,
                stmtString,
                hasMethodCalling,
                directNeighborVariableNames,
                indirectNeighborVariableNames,
                collectAtCounts,
                invokeCallCount
        );
    }


    private static String createArgStmtString(Statement stmt, int callCountAfterTargetInLine, int argIndex, MethodElementName calleeMethodName) {
        final String BG_GREEN = "\u001B[48;5;22m";
        final String RESET = "\u001B[0m";
        LexicalPreservingPrinter.setup(stmt);
        JavaParserExpressionExtractor.extractExprArg(false, stmt, callCountAfterTargetInLine, argIndex, calleeMethodName).getTokenRange().ifPresent(tokenRange -> {
                    // 先頭トークンに BG_GREEN を付与、末尾トークンに RESET を付与
                    // トークン間のスペースも含めて連続ハイライトされる
                    var first = tokenRange.getBegin();
                    var last = tokenRange.getEnd();
                    first.setText(BG_GREEN + first.getText());
                    last.setText(last.getText() + RESET);
                }
        );
        return LexicalPreservingPrinter.print(stmt);
    }

    /**
     * exprから次に探索の対象となる変数の名前を取得する。
     * exprの演算に直接用いられている変数が対象。
     */
    private List<String> extractDirectNeighborVariableNames(Expression expr){
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
    private List<String> extractIndirectNeighborVariableNames(Expression expr){
        return expr.findAll(NameExpr.class).stream()
                //引数やメソッド呼び出しに用いられる変数を除外
                .filter(nameExpr -> !nameExpr.findAncestor(MethodCallExpr.class).isEmpty())
                .map(NameExpr::toString)
                .collect(Collectors.toList());
    }

    /**
     * 文中の全メソッド呼び出しを Java の実行時評価順で取得する。
     */
    private static List<Expression> getEvalOrder(Statement stmt) {
        List<Expression> calls = new ArrayList<>();
        stmt.accept(new StatementEvalOrderVisitor(), calls);
        return calls;
    }

    /**
     * 引数式内の直接呼び出し（引数式内で親 MethodCallExpr を持たない MethodCallExpr）が
     * 評価順リスト内の何番目か（1-based）をリストで返す。
     */
    private static List<Integer> getCollectAtCounts(List<Expression> evalOrder, Expression argExpr) {
        // 引数式内の直接呼び出し（引数式内に親 MethodCallExpr を持たない）を取得
        List<MethodCallExpr> directCalls = argExpr.findAll(MethodCallExpr.class).stream()
                .filter(mce -> {
                    // mce の祖先を辿り、argExpr 内に別の MethodCallExpr がなければ直接呼び出し
                    var ancestor = mce.findAncestor(MethodCallExpr.class);
                    // 祖先が argExpr 自体か argExpr の外なら直接呼び出し
                    return ancestor.isEmpty() || !argExpr.findAll(Node.class, n -> n == ancestor.get()).stream().findAny().isPresent();
                })
                .collect(Collectors.toList());

        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < evalOrder.size(); i++) {
            Expression call = evalOrder.get(i);
            for (MethodCallExpr directCall : directCalls) {
                if (call == directCall) {
                    result.add(i + 1);
                }
            }
        }
        return result;
    }

    /**
     * invoke メソッド（引数を受け取るメソッド）が評価順リスト内の何番目か（1-based）を返す。
     */
    private static int getInvokeCallCount(List<Expression> evalOrder, Statement stmt, int callCountAfterTargetInLine, int argIndex, MethodElementName invokeMethodName) {
        Expression invokeExpr = JavaParserExpressionExtractor.extractExprArg(false, stmt, callCountAfterTargetInLine, argIndex, invokeMethodName);
        // invokeExpr は引数式。その親が invoke メソッド呼び出し。
        // 親の MethodCallExpr を見つける
        var parent = invokeExpr.getParentNode();
        while (parent.isPresent()) {
            Node p = parent.get();
            if (p instanceof MethodCallExpr) {
                for (int i = 0; i < evalOrder.size(); i++) {
                    if (evalOrder.get(i) == p) {
                        return i + 1;
                    }
                }
            }
            parent = p.getParentNode();
        }
        throw new RuntimeException("invoke メソッドが見つかりません: " + invokeMethodName + " in " + stmt);
    }

    private static String stmtToStringNoComments(Statement stmt) {
        PrinterConfiguration conf = new DefaultPrinterConfiguration()
                .removeOption(new DefaultConfigurationOption(PRINT_COMMENTS));
        return stmt.toString(conf);
    }
}
