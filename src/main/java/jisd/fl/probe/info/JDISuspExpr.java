package jisd.fl.probe.info;

import jisd.fl.probe.record.TracedValueCollection;

public class JDISuspExpr {
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
}
