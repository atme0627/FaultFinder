package jisd.fl.core.domain;

import jisd.fl.core.domain.internal.ValueChangingLineFinder;
import jisd.fl.core.domain.port.SuspiciousArgumentsSearcher;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.core.entity.element.LineElementNameResolver;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.*;
import jisd.fl.infra.javaparser.JavaParserLineElementNameResolverFactory;
import jisd.fl.infra.javaparser.JavaParserSuspiciousExpressionFactory;
import jisd.fl.infra.jdi.JDISuspiciousArgumentsSearcher;
import jisd.fl.infra.jdi.TargetVariableTracer;
import jisd.fl.core.entity.TracedValue;

import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Optional;

public class CauseLineFinder {
    private final SuspiciousExpressionFactory factory;
    private final SuspiciousArgumentsSearcher suspiciousArgumentsSearcher;
    private final TargetVariableTracer tracer;

    /**
     * 依存性注入用のコンストラクタ
     * テスト時や特定の実装を注入したい場合に使用
     */
    public CauseLineFinder(
            SuspiciousExpressionFactory factory,
            SuspiciousArgumentsSearcher suspiciousArgumentsSearcher,
            TargetVariableTracer tracer) {
        this.factory = factory;
        this.suspiciousArgumentsSearcher = suspiciousArgumentsSearcher;
        this.tracer = tracer;
    }

    /**
     * デフォルトコンストラクタ
     * 標準的な実装を使用する
     */
    public CauseLineFinder() {
        this(
            new JavaParserSuspiciousExpressionFactory(),
            new JDISuspiciousArgumentsSearcher(),
            new TargetVariableTracer()
        );
    }
    /**
     * 与えられたSuspiciousVariableに対して、その直接的な原因となるExpressionをSuspiciousExpressionとして返す
     * 原因が呼び出し元の引数にある場合は、その引数のExprに対応するものを返す
     *
     */
    public Optional<SuspiciousExpression> find(SuspiciousVariable target) {
        //ターゲット変数が変更されうる行を観測し、全変数の情報を取得
        List<TracedValue> tracedValues = tracer.traceValuesOfTarget(target);
        tracedValues.sort(TracedValue::compareTo);
        //対象の変数に変更が起き、actualを取るようになった行（原因行）を探索
        Optional<SuspiciousExpression> result = searchProbeLine(target, tracedValues);
        return result;
    }

    /**
     * 1. 代入によって変数がactualの値を取るようになったパターン
     * 1a. すでに定義されていた変数に代入が行われたパターン
     * 1b. 宣言と同時に行われた初期化によってactualの値を取るパターン
     * 2. その変数が引数由来で、かつメソッド内で上書きされていないパターン
     * 3. throw内などブレークポイントが置けない行で、代入が行われているパターン --> 未想定
     *
     * @return
     */
    private Optional<SuspiciousExpression> searchProbeLine(SuspiciousVariable target, List<TracedValue> tracedValues) {
        /* 1a. すでに定義されていた変数に代入が行われたパターン */
        //代入の実行後にactualの値に変化している行の特定(ない場合あり)
        Optional<TracedValue> changeToActualLine = valueChangedToActualLine(target, tracedValues, target.actualValue());
        //代入の実行後にactualの値に変化している行あり -> その中で最後に実行された行がprobe line
        if (changeToActualLine.isPresent()) {
            //原因行
            TracedValue causeLine = changeToActualLine.get();
            int causeLineNumber = causeLine.lineNumber;
            return Optional.of(resultIfAssigned(target, causeLineNumber));
        }

        //fieldは代入以外での値の変更を特定できない
        if (target instanceof SuspiciousFieldVariable) {
            System.err.println("Cannot find probe line of field. [FIELD NAME] " + target.variableName());
            return Optional.empty();
        }

        SuspiciousLocalVariable localVariable = (SuspiciousLocalVariable) target;
        /* 2. その変数が引数由来で、かつメソッド内で上書きされていないパターン */
        //初めて変数が観測された時点ですでにactualの値を取っている
        return resultIfNotAssigned(localVariable);

        /* 3. throw内などブレークポイントが置けない行で、代入が行われているパターン */
//            System.err.println("There is no value which same to actual.");
//            return Optional.empty();
    }


    private Optional<TracedValue> valueChangedToActualLine(SuspiciousVariable target, List<TracedValue> tracedValues, String actual) {
        //対象の変数に値の変化が起きている行の特定
        List<Integer> assignedLine = ValueChangingLineFinder.findCauseLines(target);
        return tracedValues.stream()
                .filter(tv -> assignedLine.contains(tv.lineNumber))
                .filter(tv -> tv.value.equals(actual))
                .max(TracedValue::compareTo);
    }


    /**
     * 代入によって変数がactualの値を取るようになったパターン(初期化含む)
     * 値がactualになった行の前に観測した行が、実際に値を変更した行(probe line)
     * ex.)
     * SuspClass#suspMethod(){
     * ...
     * 18: suspVar = a + 10; // <-- suspicious assignment
     * ...
     * }
     * <p>
     * 調査対象の変数がfieldの場合もあるので必ずしもsuspicious assignmentは対象の変数と同じメソッドでは起きない
     * が、同じクラスであることは保証される
     */
    private SuspiciousAssignment resultIfAssigned(SuspiciousVariable target, int causeLineNumber) {
        try {
            LineElementNameResolver resolver = JavaParserLineElementNameResolverFactory.create(target.locateClass());
            MethodElementName locateMethodElementName = resolver.lineElementAt(causeLineNumber).methodElementName;
            return factory.createAssignment(target.failedTest(), locateMethodElementName, causeLineNumber, target);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 探索対象の変数が現在実行中のメソッドの引数であり、メソッド呼び出しの時点でその値を取っていたパターン
     * メソッドの呼び出し元での対象の変数に対応する引数のExprを特定し、SuspiciousArgumentを取得する
     * <p>
     * ex.)
     * CallerClass#callerMethod(){
     * ...
     * 18: foo = calleeMethod(a + b, c);
     * //                     ^^^^^
     * //             suspicious argument
     * ...
     * }
     * <p>
     * CalleeClass#CalleeMethod(x, y){
     * //                       ^^^
     * //                 target variable
     * ...
     * }
     */
    private Optional<SuspiciousExpression> resultIfNotAssigned(SuspiciousLocalVariable target) {
        //実行しているメソッド名を取得
        MethodElementName locateMethodElementName = target.locateMethod();
        Optional<SuspiciousArgument> result = suspiciousArgumentsSearcher.searchSuspiciousArgument(target, locateMethodElementName);
        if(result.isEmpty()){
            return Optional.empty();
        }
        else {
            return Optional.of(result.get());
        }
    }
}
