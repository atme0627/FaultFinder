package jisd.fl.core.domain;

import jisd.fl.core.domain.port.TraceValueAtSuspiciousExpressionStrategy;
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

    private final TraceValueAtSuspiciousExpressionStrategy assignmentStrategy;
    private final TraceValueAtSuspiciousExpressionStrategy returnValueStrategy;
    private final TraceValueAtSuspiciousExpressionStrategy argumentStrategy;

    public ValueAtSuspiciousExpressionTracer(){
        assignmentStrategy = new JDITraceValueAtSuspiciousAssignmentStrategy();
        returnValueStrategy = new JDITraceValueAtSuspiciousReturnValueStrategy();
        argumentStrategy = new JDITraceValueAtSuspiciousArgumentStrategy();
    }
    public TracedValueCollection traceAll(SuspiciousExpression suspExpr){
        return switch (suspExpr) {
            case SuspiciousAssignment suspAssign -> assignmentStrategy.traceAllValuesAtSuspExpr(suspAssign);
            case SuspiciousReturnValue suspReturn -> returnValueStrategy.traceAllValuesAtSuspExpr(suspReturn);
            case SuspiciousArgument suspArg -> argumentStrategy.traceAllValuesAtSuspExpr(suspArg);
            default -> throw new IllegalStateException("Unexpected value: " + suspExpr);
        };
    }

}
