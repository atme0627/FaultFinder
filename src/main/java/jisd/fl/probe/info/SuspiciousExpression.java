package jisd.fl.probe.info;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.util.analyze.CodeElementName;
import jisd.fl.util.analyze.JavaParserUtil;

import javax.validation.constraints.NotNull;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

//木構造にしてvisualizationをできるようにしたい
public abstract class SuspiciousExpression {
    //どのテスト実行時の話かを指定
    protected final CodeElementName failedTest;
    protected final CodeElementName locateClass;
    protected final int locateLine;
    protected final Statement stmt;
    @NotNull protected  Expression expr;
    protected final String actualValue;

    protected SuspiciousExpression(CodeElementName failedTest, CodeElementName locateClass, int locateLine, String actualValue) {
        this.failedTest = failedTest;
        this.locateClass = locateClass;
        this.locateLine = locateLine;
        this.actualValue = actualValue;
        this.stmt = getStmt();
    }

    /**
     * デバッグ対象プログラム内の行の、ある特定の実行に対してその中で呼び出されたすべてのメソッドのreturn行を特定する
     * 一般に、コード行は複数実行され得るため、行番号の指定のみではプログラム状態が特定したい状況のものと同一であることを保証できない。
     * したがって、行番号 + 監視対象の変数が取る値 + (今後: メソッドが実行されるオブジェクトのID)によって、目的の行実行を特定する。
     * 監視対象が変数の場合、step実行後に、その値を見れば良い
     * 監視対象がreturn文の演算結果の場合、methodExitEventのreturnValue()を見れば良い
     *
     * @return return文を表すSuspiciousReturnValueのリスト
     */
    abstract public List<SuspiciousReturnValue> searchSuspiciousReturns() throws NoSuchElementException;


    abstract public List<SuspiciousVariable> neighborSuspiciousVariables();
    abstract protected Expression extractExpr();

    /**
     * exprから次に探索の対象となる変数の名前を取得する。
     * exprの演算に直接用いられている変数のみが対象で、引数やメソッド呼び出しの対象となる変数は除外する。
     * @return 変数名のリスト
     */
    protected List<String> extractNeighborVariableNames(){
        return expr.findAll(NameExpr.class).stream()
                //引数やメソッド呼び出しに用いられる変数を除外
                .filter(nameExpr -> nameExpr.findAncestor(MethodCallExpr.class).isEmpty())
                .map(NameExpr::toString)
                .collect(Collectors.toList());
    }

    private Statement getStmt(){
        try {
            return JavaParserUtil.getStatementByLine(locateClass, locateLine).orElseThrow();
        } catch (NoSuchFileException e) {
            throw new RuntimeException("Class [" + locateClass + "] is not found.");
        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract Statement from [" + locateClass + ":" + locateLine + "].");
        }
    }

    @Override
    public String toString(){
        return  "    " + locateLine + ": " + String.format("%-50s", stmt.toString()) +
                String.format(" == %-8s", actualValue) +
                "    At " + locateClass;
    }
}
