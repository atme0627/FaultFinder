package jisd.fl.core.domain;

import jisd.fl.core.domain.internal.ValueChangingLineFinder;
import jisd.fl.core.domain.port.SuspiciousArgumentsSearcher;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousArgument;
import jisd.fl.core.entity.susp.SuspiciousAssignment;
import jisd.fl.infra.javaparser.JavaParserSuspiciousExpressionFactory;
import jisd.fl.infra.javaparser.TmpJavaParserUtils;
import jisd.fl.infra.jdi.JDISuspiciousArgumentsSearcher;
import jisd.fl.infra.jdi.TargetVariableTracer;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.util.analyze.StaticAnalyzer;

import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CauseLineFinder {
    SuspiciousVariable target;
    SuspiciousExpressionFactory factory;
    SuspiciousArgumentsSearcher suspiciousArgumentsSearcher;
    TargetVariableTracer tracer;

    public CauseLineFinder(SuspiciousVariable target) {
        this.target = target;
        this.factory = new JavaParserSuspiciousExpressionFactory();
        this.suspiciousArgumentsSearcher = new JDISuspiciousArgumentsSearcher();
        this.tracer = new TargetVariableTracer(target);
    }
    /**
     * 与えられたSuspiciousVariableに対して、その直接的な原因となるExpressionをSuspiciousExpressionとして返す
     * 原因が呼び出し元の引数にある場合は、その引数のExprに対応するものを返す
     *
     */
    public Optional<SuspiciousExpression> find() {
        //ターゲット変数が変更されうる行を観測し、全変数の情報を取得
        List<TracedValue> tracedValues = tracer.traceValuesOfTarget();
        tracedValues.sort(TracedValue::compareTo);
        for(TracedValue tv : tracedValues){
            System.out.println("     " + tv);
        }
        //対象の変数に変更が起き、actualを取るようになった行（原因行）を探索
        Optional<SuspiciousExpression> result = searchProbeLine(tracedValues);
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
    private Optional<SuspiciousExpression> searchProbeLine(List<TracedValue> tracedValues) {
        /* 1a. すでに定義されていた変数に代入が行われたパターン */
        //代入の実行後にactualの値に変化している行の特定(ない場合あり)
        List<TracedValue> changeToActualLines = valueChangedToActualLine(tracedValues, target.getActualValue());
        //代入の実行後にactualの値に変化している行あり -> その中で最後に実行された行がprobe line
        if (!changeToActualLines.isEmpty()) {
            //原因行
            TracedValue causeLine = changeToActualLines.get(changeToActualLines.size() - 1);
            int causeLineNumber = causeLine.lineNumber;
            return Optional.of(resultIfAssigned(causeLineNumber));
        }

        //fieldは代入以外での値の変更を特定できない
        if (target.isField()) {
            System.err.println("Cannot find probe line of field. [FIELD NAME] " + target.getSimpleVariableName());
            return Optional.empty();
        }

        /* 2. その変数が引数由来で、かつメソッド内で上書きされていないパターン */
        //初めて変数が観測された時点ですでにactualの値を取っている
        return resultIfNotAssigned();

        /* 3. throw内などブレークポイントが置けない行で、代入が行われているパターン */
//            System.err.println("There is no value which same to actual.");
//            return Optional.empty();
    }


    private List<TracedValue> valueChangedToActualLine(List<TracedValue> tracedValues, String actual) {
        //対象の変数に値の変化が起きている行の特定
        List<Integer> assignedLine = ValueChangingLineFinder.find(target);
        //対象の変数を定義している行を追加
        assignedLine.addAll(
                //targetVariableのVariableDeclaratorを特定
                StaticAnalyzer.findLocalVarDeclaration(target.getLocateMethodElement(), target.getSimpleVariableName())
                        .stream()
                        .map(vd -> vd.getRange().get().begin.line)
                        .toList()
        );

        List<TracedValue> changedToActualLines = new ArrayList<>();
        for (int i = 0; i < tracedValues.size() - 1; i++) {
            TracedValue watchingLine = tracedValues.get(i);
            //watchingLineでは代入が行われていない -> 原因行ではない
            if (!assignedLine.contains(watchingLine.lineNumber)) continue;
            //次の行で値がactualに変わっている -> その行が原因行の候補
            TracedValue afterAssignLine = tracedValues.get(i + 1);
            if (afterAssignLine.value.equals(actual)) changedToActualLines.add(watchingLine);
        }
        changedToActualLines.sort(TracedValue::compareTo);
        return changedToActualLines;
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
    private SuspiciousAssignment resultIfAssigned(int causeLineNumber) {
        try {
            //TODO: 毎回静的解析するのは遅すぎるため、キャッシュする方がいい
            Map<Integer, MethodElementName> methodElementNames = StaticAnalyzer.getMethodNamesWithLine(target.getLocateMethodElement());
            MethodElementName locateMethodElementName = methodElementNames.get(causeLineNumber);
            return factory.createAssignment(target.getFailedTest(), locateMethodElementName, causeLineNumber, target);
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
    private Optional<SuspiciousExpression> resultIfNotAssigned() {
        //実行しているメソッド名を取得
        MethodElementName locateMethodElementName = target.getLocateMethodElement();
        Optional<SuspiciousArgument> result = suspiciousArgumentsSearcher.searchSuspiciousArgument(target, locateMethodElementName);
        if(result.isEmpty()){
            return Optional.empty();
        }
        else {
            return Optional.of(result.get());
        }
    }
}
