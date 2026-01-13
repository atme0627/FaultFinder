package jisd.fl.core.domain;

import jisd.fl.infra.jdi.JDITraceValueAtSuspiciousArgumentStrategy;
import jisd.fl.infra.jdi.JDITraceValueAtSuspiciousAssignmentStrategy;
import jisd.fl.infra.jdi.JDITraceValueAtSuspiciousReturnValueStrategy;
import jisd.fl.probe.info.*;
import jisd.fl.probe.record.TracedValueCollection;

public class ValueAtSuspiciousExpressionTracer {
    /**
     * このSuspiciousExprで観測できる全ての変数とその値の情報をJISDを用いて取得
     * 複数回SuspiciousExpressionが実行されているときは、最後に実行された時の値を使用する
     * @return
     */
    public TracedValueCollection traceAllAtSuspiciousExpression(SuspiciousExpression suspExpr){
        return switch (suspExpr) {
            case SuspiciousAssignment thisSuspAssign ->
                    JDITraceValueAtSuspiciousAssignmentStrategy.traceAllValuesAtSuspExpr(thisSuspAssign);
            case SuspiciousReturnValue thisSuspReturn ->
                    JDITraceValueAtSuspiciousReturnValueStrategy.traceAllValuesAtSuspExpr(thisSuspReturn);
            case SuspiciousArgument thisSuspArg -> JDITraceValueAtSuspiciousArgumentStrategy.traceAllValuesAtSuspExpr(thisSuspArg);
            default -> throw new IllegalStateException("Unexpected value: " + suspExpr);
        };
    }

}
