package jisd.fl.infra.jdi;

import jisd.fl.core.domain.port.ValueTracer;
import jisd.fl.probe.info.*;
import jisd.fl.probe.record.TracedValueCollection;

public class JDIValueTracer implements ValueTracer {
    /**
     * このSuspiciousExprで観測できる全ての変数とその値の情報をJISDを用いて取得
     * 複数回SuspiciousExpressionが実行されているときは、最後に実行された時の値を使用する
     * @return
     */
    public TracedValueCollection traceAllAtSuspiciousExpression(SuspiciousExpression suspExpr){
        return switch (suspExpr) {
            case SuspiciousAssignment thisSuspAssign ->
                    JDISuspAssign.traceAllValuesAtSuspExpr(thisSuspAssign);
            case SuspiciousReturnValue thisSuspReturn ->
                    JDISuspReturn.traceAllValuesAtSuspExpr(thisSuspReturn);
            case SuspiciousArgument thisSuspArg -> JDISuspArg.traceAllValuesAtSuspExpr(thisSuspArg);
            default -> throw new IllegalStateException("Unexpected value: " + suspExpr);
        };
    }

}
