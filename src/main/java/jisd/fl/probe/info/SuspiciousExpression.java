package jisd.fl.probe.info;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.sun.jdi.*;
import jisd.fl.probe.record.TracedValue;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.sbfl.coverage.Granularity;
import jisd.fl.util.analyze.CodeElementName;
import jisd.fl.util.analyze.MethodElementName;
import jisd.fl.util.analyze.JavaParserUtil;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.nio.file.NoSuchFileException;
import java.time.LocalDateTime;
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
        this.stmt = extractStmt();
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
            return JavaParserUtil.getStatementByLine(locateMethod, locateLine).orElseThrow();
        } catch (NoSuchFileException e) {
            throw new RuntimeException("Class [" + locateMethod + "] is not found.");
        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract Statement from [" + locateMethod + ":" + locateLine + "].");
        }
    }

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


    protected static int getCallStackDepth(ThreadReference th){
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
    protected abstract TracedValueCollection traceAllValuesAtSuspExpr(int sleepTime);

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
        //TODO: 今の実装だと、変数がローカルかフィールドか区別できない
        // ex. this.x = x の時, this.xも探索してしまう。
        List<SuspiciousVariable> result =
                tracedNeighborValue.getAll().stream()
                .filter(t -> neighborVariableNames.contains(t.variableName))
                .filter(t -> !t.isReference)
                        //
                .map(t -> new SuspiciousVariable(
                        failedTest,
                        locateMethod.getFullyQualifiedMethodName(),
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

    public CodeElementName convertToCodeElementName(Granularity granularity){
        return switch (granularity){
            case LINE -> locateMethod.toLineElementName(locateLine);
            case METHOD, CLASS -> locateMethod;
        };
    }

    protected List<TracedValue> watchAllVariablesInLine(StackFrame frame){
        List<TracedValue> result = new ArrayList<>();

        // （1）ローカル変数
        List<LocalVariable> locals;
        try {
            locals = frame.visibleVariables();
        } catch (AbsentInformationException e) {
            throw new RuntimeException(e);
        }
        Map<LocalVariable, Value> localVals = frame.getValues(locals);
        localVals.forEach((lv, v) -> {
            if(v == null) return;
            //配列の場合[0]のみ観測
            if(v instanceof ArrayReference ar){
                if(ar.length() == 0) return;
                result.add(new TracedValue(
                        LocalDateTime.MIN,
                        lv.name() + "[0]",
                        getValueString(ar.getValue(0)),
                        locateLine
                ));
            }

            result.add(new TracedValue(
                    LocalDateTime.MIN,
                    lv.name(),
                    getValueString(v),
                    locateLine
            ));
        });

        // (2) インスタンスフィールド
        ObjectReference thisObj = frame.thisObject();
        if (thisObj != null) {
            ReferenceType  rt = thisObj.referenceType();
            for (Field f : rt.visibleFields()) {
                if (f.isStatic()) continue;
                result.add(new TracedValue(
                        LocalDateTime.MIN,
                        "this." + f.name(),
                        getValueString(thisObj.getValue(f)),
                        locateLine
                ));
            }
        }

        // (3) static フィールド
        ReferenceType rt = frame.location().declaringType();
        for (Field f : rt.visibleFields()) {
            if (!f.isStatic()) continue;
            result.add(new TracedValue(
                    LocalDateTime.MIN,
                    "this." + f.name(),
                    getValueString(rt.getValue(f)),
                    locateLine
            ));
        }
        return result;
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

    //Jackson デシリアライズ用メソッド
    public static SuspiciousExpression loadFromJson(File f){
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(f, SuspiciousExpression.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getValueString(Value v){
        if(v == null) return "null";
        if(v instanceof ObjectReference obj){
            if(isPrimitiveWrapper(obj.referenceType())) {
                try {
                    Field valueField = obj.referenceType().fieldByName("value");
                    Value primitiveValue = obj.getValue(valueField);
                    return primitiveValue.toString();
                } catch (Exception e) {
                    return v.toString();
                }
            }
            return v.toString();
        }
        return v.toString();
    }

    protected static boolean isPrimitiveWrapper(Type type) {
        //プリミティブ型のラッパークラスの名前
        final Set<String> WRAPPER_CLASS_NAMES = new HashSet<>(Arrays.asList(
                Boolean.class.getName(),
                Byte.class.getName(),
                Character.class.getName(),
                Short.class.getName(),
                Integer.class.getName(),
                Long.class.getName(),
                Float.class.getName(),
                Double.class.getName(),
                Void.class.getName()
        ));

        if (type instanceof ClassType) {
            return WRAPPER_CLASS_NAMES.contains(type.name());
        }
        return false;
    }
}
