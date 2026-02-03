package jisd.fl.core.domain;

import jisd.fl.core.domain.internal.ValueAtSuspiciousExpressionTracer;
import jisd.fl.core.entity.susp.SuspiciousFieldVariable;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.core.entity.susp.SuspiciousVariable;

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
        List<TracedValue> tracedNeighborValue = tracer.traceAll(suspExpr);
        //SuspExpr内で使用されている変数を静的解析により取得
        List<String> neighborVariableNames = new ArrayList<>(suspExpr.directNeighborVariableNames);
        if(includeIndirectUsedVariable) neighborVariableNames.addAll(suspExpr.indirectNeighborVariableNames);

        //TODO: 今の実装だと配列のフィルタリングがうまくいかない
        //TODO: 今の実装だと、変数がローカルかフィールドか区別できない
        // ex. this.x = x の時, this.xも探索してしまう。
        List<SuspiciousVariable> result =
                tracedNeighborValue.stream()
                        .filter(t -> neighborVariableNames.contains(t.variableName))
                        .filter(t -> !t.isReference)
                        //
                        .map(t -> {
                            return (t.isField) ?
                            new SuspiciousFieldVariable(
                                    suspExpr.failedTest,
                                    suspExpr.locateMethod.classElementName,
                                    t.variableName,
                                    t.value,
                                    true
                            ) :
                            new SuspiciousLocalVariable(
                                    suspExpr.failedTest,
                                    suspExpr.locateMethod.fullyQualifiedName(),
                                    t.variableName,
                                    t.value,
                                    true
                            );
                        }).distinct().collect(Collectors.toList());
        return result;
    }
}
