package jisd.fl.probe.info;

import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.probe.record.TracedValueCollection;

import java.util.List;
import java.util.stream.Collectors;

public class JDISuspExpr {
    /**
     * このSuspiciousExprで観測できる全ての変数とその値の情報をJISDを用いて取得
     * 複数回SuspiciousExpressionが実行されているときは、最後に実行された時の値を使用する
     * @param sleepTime
     * @return
     */
    static TracedValueCollection traceAllValuesAtSuspExpr(int sleepTime, SuspiciousExpression thisSuspExpr){
        return switch (thisSuspExpr) {
            case SuspiciousAssignment thisSuspAssign ->
                    JDISuspAssign.traceAllValuesAtSuspExpr(sleepTime, thisSuspAssign);
            case SuspiciousReturnValue thisSuspReturn ->
                    JDISuspReturn.traceAllValuesAtSuspExpr(sleepTime, thisSuspReturn);
            case SuspiciousArgument thisSuspArg -> JDISuspArg.traceAllValuesAtSuspExpr(sleepTime, thisSuspArg);
            default -> throw new IllegalStateException("Unexpected value: " + thisSuspExpr);
        };
    }

    static public List<SuspiciousVariable> neighborSuspiciousVariables(int sleepTime, boolean includeIndirectUsedVariable, SuspiciousExpression suspExpr){
        //SuspExprで観測できる全ての変数
        TracedValueCollection tracedNeighborValue = JDISuspExpr.traceAllValuesAtSuspExpr(sleepTime, suspExpr);
        //SuspExpr内で使用されている変数を静的解析により取得
        List<String> neighborVariableNames = TmpJavaParserUtils.extractNeighborVariableNames(suspExpr.expr, includeIndirectUsedVariable);

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

        result.forEach(sv -> sv.setParent(suspExpr));
        return result;
    }
}
