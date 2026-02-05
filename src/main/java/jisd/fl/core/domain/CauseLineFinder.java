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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Optional;

public class CauseLineFinder {
    private static final Logger logger = LoggerFactory.getLogger(CauseLineFinder.class);

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
     * 与えられた Suspicious Variable に対して、その直接的な原因となる Expression を探索する。
     * <p>
     * 変数が actual 値を取るようになった原因を以下のパターンで特定する：
     * <ul>
     *   <li>Pattern 1: 代入による値の変更（初期化を含む）</li>
     *   <li>Pattern 2: 引数として渡された値（メソッド呼び出し元）</li>
     * </ul>
     *
     * @param target 調査対象の suspicious variable
     * @return 原因となる suspicious expression。見つからない場合は empty
     */
    public Optional<SuspiciousExpression> find(SuspiciousVariable target) {
        // ターゲット変数が変更されうる行を観測し、全変数の情報を取得
        List<TracedValue> tracedValues = tracer.traceValuesOfTarget(target);
        tracedValues.sort(TracedValue::compareTo);
        // 対象の変数に変更が起き、actual を取るようになった行（原因行）を探索
        return searchProbeLine(target, tracedValues);
    }

    /**
     * 変数が actual 値を取るようになった原因行（probe line）を探索する。
     * <p>
     * 以下のパターンに対応：
     * <ul>
     *   <li>Pattern 1a: すでに定義されていた変数への代入</li>
     *   <li>Pattern 1b: 宣言と同時の初期化</li>
     *   <li>Pattern 2: 引数由来（メソッド内で上書きされていない）</li>
     *   <li>Pattern 3: throw 内など（未実装）</li>
     * </ul>
     *
     * @param target 調査対象の suspicious variable
     * @param tracedValues 観測された変数の値の履歴（時系列順）
     * @return 原因となる suspicious expression。見つからない場合は empty
     */
    private Optional<SuspiciousExpression> searchProbeLine(SuspiciousVariable target, List<TracedValue> tracedValues) {
        // Pattern 1: 代入による値の変更
        Optional<TracedValue> changeToActualLine = valueChangedToActualLine(target, tracedValues, target.actualValue());
        if (changeToActualLine.isPresent()) {
            return Optional.of(resultIfAssigned(target, changeToActualLine.get().lineNumber));
        }

        // Field 変数は代入以外での値の変更を特定できない
        if (target instanceof SuspiciousFieldVariable) {
            logger.warn("Cannot find probe line of field. [FIELD NAME] {}", target.variableName());
            return Optional.empty();
        }

        // Pattern 2: 引数由来（メソッド内で上書きされていない）
        SuspiciousLocalVariable localVariable = (SuspiciousLocalVariable) target;
        return resultIfNotAssigned(localVariable);

        // TODO: Pattern 3 の実装
        // throw 内などブレークポイントが置けない行で、代入が行われているパターンへの対応
        // 現在は未実装。静的解析の拡張が必要。
    }


    /**
     * 実行後に actual 値を取るようになった行を特定する。
     * <p>
     * post-state 観測により、各 TracedValue はその行の実行後の値を持つ。
     * 代入が行われた行の中で、実行後に actual 値を取った最後の行を返す。
     *
     * @param target 調査対象の suspicious variable
     * @param tracedValues 観測された変数の値の履歴（時系列順）
     * @param actual 期待される actual 値
     * @return 実行後に actual 値を取った最後の行。見つからない場合は empty
     */
    private Optional<TracedValue> valueChangedToActualLine(SuspiciousVariable target, List<TracedValue> tracedValues, String actual) {
        // 対象の変数に値の変化が起きている行の特定
        List<Integer> assignedLine = ValueChangingLineFinder.findBreakpointLines(target);
        return tracedValues.stream()
                .filter(tv -> assignedLine.contains(tv.lineNumber))
                .filter(tv -> tv.value.equals(actual))
                .max(TracedValue::compareTo);
    }


    /**
     * Pattern 1: 代入による値の変更（初期化を含む）の場合の SuspiciousAssignment を生成する。
     * <p>
     * 例：
     * <pre>
     * SuspClass#suspMethod() {
     *     ...
     *     18: suspVar = a + 10;  // <-- suspicious assignment
     *     ...
     * }
     * </pre>
     * <p>
     * 注：field 変数の場合、代入行は対象変数と同じメソッド内とは限らないが、
     * 同じクラス内であることは保証される。
     *
     * @param target 調査対象の suspicious variable
     * @param causeLineNumber 原因となる代入が行われた行番号
     * @return 代入を表す SuspiciousAssignment
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
     * Pattern 2: 引数由来（メソッド内で上書きされていない）の場合の SuspiciousArgument を取得する。
     * <p>
     * 変数がメソッドの引数であり、メソッド呼び出し時点で actual 値を持っていた場合、
     * 呼び出し元での対応する引数式を suspicious argument として返す。
     * <p>
     * 例：
     * <pre>
     * CallerClass#callerMethod() {
     *     ...
     *     18: foo = calleeMethod(a + b, c);  // <-- suspicious argument: a + b
     *     ...
     * }
     *
     * CalleeClass#calleeMethod(x, y) {  // <-- target variable: x
     *     ...
     * }
     * </pre>
     *
     * @param target 調査対象の suspicious local variable（引数）
     * @return 呼び出し元の引数式を表す SuspiciousArgument。見つからない場合は empty
     */
    private Optional<SuspiciousExpression> resultIfNotAssigned(SuspiciousLocalVariable target) {
        // 実行しているメソッド名を取得
        MethodElementName locateMethodElementName = target.locateMethod();
        return suspiciousArgumentsSearcher.searchSuspiciousArgument(target, locateMethodElementName)
                .map(arg -> arg);
    }
}
