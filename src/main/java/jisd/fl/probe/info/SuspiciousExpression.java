package jisd.fl.probe.info;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.InvalidStackFrameException;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.debug.Location;
import jisd.debug.Point;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.probe.record.TracedValuesAtLine;
import jisd.fl.util.QuietStdOut;
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.CodeElementName;
import jisd.fl.util.analyze.JavaParserUtil;

import javax.validation.constraints.NotNull;
import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.stream.Collectors;

public abstract class SuspiciousExpression {
    //どのテスト実行時の話かを指定
    protected final CodeElementName failedTest;
    protected final CodeElementName locateClass;
    protected final int locateLine;
    protected final Statement stmt;
    @NotNull protected  Expression expr;
    protected final String actualValue;
    //木構造にしてvisualizationをできるようにする
    //保持するのは自分の子要素のみ
    List<SuspiciousExpression> childSuspExprs = new ArrayList<>();

    protected SuspiciousExpression(CodeElementName failedTest, CodeElementName locateClass, int locateLine, String actualValue) {
        this.failedTest = failedTest;
        this.locateClass = locateClass;
        this.locateLine = locateLine;
        this.actualValue = actualValue;
        this.stmt = extractStmt();
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

    abstract protected Expression extractExpr();

    /**
     * exprから次に探索の対象となる変数の名前を取得する。
     * exprの演算に直接用いられている変数のみが対象で、引数やメソッド呼び出しの対象となる変数は除外する。
     * @return 変数名のリスト
     */
    protected List<String> extractNeighborVariableNames(boolean includeIndirectUsedVariable){
        return expr.findAll(NameExpr.class).stream()
                //引数やメソッド呼び出しに用いられる変数を除外
                .filter(nameExpr -> includeIndirectUsedVariable || nameExpr.findAncestor(MethodCallExpr.class).isEmpty())
                .map(NameExpr::toString)
                .collect(Collectors.toList());
    }

    /**
     * exprにメソッド呼び出しが含まれているかを判定
     */
    protected boolean hasMethodCalling(){
        return !expr.findAll(MethodCallExpr.class).isEmpty();
    }

    private Statement extractStmt(){
        try {
            return JavaParserUtil.getStatementByLine(locateClass, locateLine).orElseThrow();
        } catch (NoSuchFileException e) {
            throw new RuntimeException("Class [" + locateClass + "] is not found.");
        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract Statement from [" + locateClass + ":" + locateLine + "].");
        }
    }

    public Statement getStmt(){
        return stmt;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("        // At ");
        sb.append(locateClass);
        sb.append("\n");
        sb.append(String.format(
                locateLine + ": " + "    " + String.format("%-50s", stmt.toString()) +
                        String.format(" == %-8s", actualValue)
        ));
        sb.append("\n");
        return sb.toString();
    }

    protected int getCallStackDepth(ThreadReference th){
        try {
            return th.frameCount();
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * このSuspiciousExprで観測できる全ての変数とその値の情報をJISDを用いて取得
     * 複数回SuspiciousExpressionが実行されているときは、最後に実行された時の値を使用する
     * @param sleepTime
     * @return
     */
    protected TracedValueCollection traceAllValuesAtSuspExpr(int sleepTime){
        try (QuietStdOut q = QuietStdOut.suppress()) {

            //デバッガ生成
            Debugger dbg = TestUtil.testDebuggerFactory(failedTest);
            dbg.setMain(this.locateClass.getFullyQualifiedClassName());
            Optional<Point> watchPointAtLine = dbg.watch(this.locateLine);

            //locateLineで観測できる全ての変数を取得
            try {
                dbg.run(sleepTime);
            } catch (VMDisconnectedException | InvalidStackFrameException ignored) {
            }

            Map<String, DebugResult> drs = watchPointAtLine.get().getResults();

            //SuspExpr内で使用されている変数の情報のみ取り出す
            //SuspiciousVariableがActualValueをとっている瞬間のものを取得するのは面倒なため
            //SuspExprが複数回実行されているときは最後に観測された値を採用する。
            List<ValueInfo> valuesAtLine = drs.entrySet().stream()
                    .map(Map.Entry::getValue)
                    .map(DebugResult::getValues)
                    .map(vis -> vis.get(vis.size() - 1))
                    .collect(Collectors.toList());

            //TODO: Locationに依存しない形にしたい
            Location loc = drs.values().stream().findFirst().get().getLocation();
            TracedValueCollection watchedValues = new TracedValuesAtLine(valuesAtLine, loc);
            dbg.exit();
            dbg.clearResults();
            System.out.println("[[[TEST 5]]]");
            return watchedValues;
        }
    }

    /**
     * 次の探索対象の変数としてこのSuspiciousExpr内で使用されている他の変数をSuspiciousVariableとして取得
     * @param sleepTime
     * @return
     */
    public List<SuspiciousVariable> neighborSuspiciousVariables(int sleepTime, boolean includeIndirectUsedVariable){
        //SuspExprで観測できる全ての変数
        TracedValueCollection tracedNeighborValue = traceAllValuesAtSuspExpr(sleepTime);
        //SuspExpr内で使用されている変数を静的解析により取得
        List<String> neighborVariableNames = extractNeighborVariableNames(includeIndirectUsedVariable);

        //TODO: 今の実装だと配列のフィルタリングがうまくいかない
        List<SuspiciousVariable> result =
                tracedNeighborValue.getAll().stream()
                .filter(t -> neighborVariableNames.contains(t.variableName))
                .filter(t -> !t.isReference)
                .map(t -> new SuspiciousVariable(
                        failedTest,
                        locateClass.getFullyQualifiedMethodName(),
                        t.variableName,
                        t.value,
                        true,
                        t.variableName.contains("[")
                )).distinct().collect(Collectors.toList());

        result.forEach(sv -> sv.setParent(this));
        return result;
    }

    public void addChild(SuspiciousExpression ch){
        this.childSuspExprs.add(ch);
    }
}
