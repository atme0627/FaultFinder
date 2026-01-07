package jisd.fl.probe.info;

import com.fasterxml.jackson.annotation.*;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.sbfl.coverage.Granularity;
import jisd.fl.core.entity.CodeElementIdentifier;
import jisd.fl.core.entity.MethodElementName;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SuspiciousAssignment.class, name = "assign"),
        @JsonSubTypes.Type(value = SuspiciousReturnValue.class, name = "return"),
        @JsonSubTypes.Type(value = SuspiciousArgument.class, name = "argument")
})

@JsonPropertyOrder({ "failedTest", "locateMethod", "locateLine", "stmt", "expr", "actualValue", "children" })

public abstract class SuspiciousExpression {
    //どのテスト実行時の話かを指定
    protected final MethodElementName failedTest;
    //フィールドの場合は<ulinit>で良い
    protected final MethodElementName locateMethod;
    protected final int locateLine;
    @JsonIgnore protected final Statement stmt;
    @JsonIgnore @NotNull protected  Expression expr;
    protected final String actualValue;
    //木構造にしてvisualizationをできるようにする
    //保持するのは自分の子要素のみ
    List<SuspiciousExpression> childSuspExprs = new ArrayList<>();

    protected SuspiciousExpression(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue) {
        this.failedTest = failedTest;
        this.locateMethod = locateMethod;
        this.locateLine = locateLine;
        this.actualValue = actualValue;
        this.stmt = TmpJavaParserUtils.extractStmt(this.locateMethod, this.locateLine);
    }

    @JsonCreator
    protected SuspiciousExpression(
            @JsonProperty("failedTest") String failedTest,
            @JsonProperty("locateMethod") String locateMethod,
            @JsonProperty("locateLine") int locateLine,
            @JsonProperty("actualValue") String actualValue,
            @JsonProperty("children") List<SuspiciousExpression> children
    ){
        this.failedTest = new MethodElementName(failedTest);
        this.locateMethod = new MethodElementName(locateMethod);
        this.locateLine = locateLine;
        this.actualValue = actualValue;
        this.childSuspExprs = children;
        this.stmt = TmpJavaParserUtils.extractStmt(this.locateMethod, this.locateLine);
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

    public Statement getStmt(){
        return stmt;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("        // At ");
        sb.append(locateMethod);
        sb.append("\n");
        sb.append(String.format(
                "%d: %s%-50s %s%s",
                locateLine,
                "    ",
                stmt.toString(),
                " == ",
                actualValue
        ));
        sb.append("\n");
        return sb.toString();
    }


    /**
     * 次の探索対象の変数としてこのSuspiciousExpr内で使用されている他の変数をSuspiciousVariableとして取得
     *
     * @param sleepTime
     * @param suspExpr
     * @return
     */
    public List<SuspiciousVariable> neighborSuspiciousVariables(int sleepTime, boolean includeIndirectUsedVariable, SuspiciousExpression suspExpr){
        //SuspExprで観測できる全ての変数
        TracedValueCollection tracedNeighborValue = JDISuspExpr.traceAllValuesAtSuspExpr(sleepTime, suspExpr);
        //SuspExpr内で使用されている変数を静的解析により取得
        List<String> neighborVariableNames = TmpJavaParserUtils.extractNeighborVariableNames(suspExpr.expr, includeIndirectUsedVariable || suspExpr.childSuspExprs.isEmpty());

        //TODO: 今の実装だと配列のフィルタリングがうまくいかない
        //TODO: 今の実装だと、変数がローカルかフィールドか区別できない
        // ex. this.x = x の時, this.xも探索してしまう。
        List<SuspiciousVariable> result =
                tracedNeighborValue.getAll().stream()
                .filter(t -> neighborVariableNames.contains(t.variableName))
                .filter(t -> !t.isReference)
                        //
                .map(t -> new SuspiciousVariable(
                        suspExpr.failedTest,
                        suspExpr.locateMethod.getFullyQualifiedMethodName(),
                        t.variableName,
                        t.value,
                        true,
                        t.isField
                )).distinct().collect(Collectors.toList());

        result.forEach(sv -> sv.setParent(this));
        return result;
    }

    public void addChild(SuspiciousExpression ch){
        this.childSuspExprs.add(ch);
    }

    public void addChild(List<? extends SuspiciousExpression> chs){
        this.childSuspExprs.addAll(chs);
    }

    public CodeElementIdentifier convertToCodeElementName(Granularity granularity){
        return switch (granularity){
            case LINE -> locateMethod.toLineElementName(locateLine);
            case METHOD, CLASS -> locateMethod;
        };
    }


    //Jackson シリアライズ用メソッド
    @JsonProperty("failedTest")
    public String getFailedTest() {
        return failedTest.toString();
    }

    @JsonProperty("locateMethod")
    public String getLocateMethod() {
        return locateMethod.toString();
    }

    @JsonProperty("locateLine")
    public int getLocateLine() {
        return locateLine;
    }

    @JsonProperty("stmt")
    public String getStatementStr() {
        return stmt.toString();
    }

    @JsonProperty("expr")
    public String getExpressionStr() {
        return expr.toString();
    }

    @JsonProperty("actualValue")
    public String getActualValue() {
        return actualValue;
    }

    @JsonProperty("children")
    public List<SuspiciousExpression> getChildren() {
        return childSuspExprs;
    }

    @Override
    public boolean equals(Object obj){
        if(!(obj instanceof SuspiciousExpression se)) return false;
        return this.failedTest.equals(se.failedTest) &&
                this.locateMethod.equals(se.locateMethod) &&
                this.locateLine == se.locateLine &&
                this.actualValue.equals(se.actualValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(failedTest, locateMethod, locateLine, actualValue);
    }
}
