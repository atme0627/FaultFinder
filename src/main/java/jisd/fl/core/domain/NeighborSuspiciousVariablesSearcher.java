package jisd.fl.core.domain;

import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.probe.record.TracedValueCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NeighborSuspiciousVariablesSearcher {
    private final ValueAtSuspiciousExpressionTracer tracer;

    public NeighborSuspiciousVariablesSearcher(){
        this.tracer = new ValueAtSuspiciousExpressionTracer();
    }
    public List<SuspiciousVariable> neighborSuspiciousVariables(boolean includeIndirectUsedVariable, SuspiciousExpression suspExpr){
        //SuspExprで観測できる全ての変数
        TracedValueCollection tracedNeighborValue = tracer.traceAll(suspExpr);
        //SuspExpr内で使用されている変数を静的解析により取得
        List<String> neighborVariableNames = new ArrayList<>(suspExpr.directNeighborVariableNames);
        if(includeIndirectUsedVariable) neighborVariableNames.addAll(suspExpr.indirectNeighborVariableNames);

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
