package jisd.fl.core.domain.port;

import jisd.fl.probe.info.SuspiciousAssignment;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.record.TracedValueCollection;

public interface TraceValueAtSuspiciousExpressionStrategy {
    TracedValueCollection traceAllValuesAtSuspExpr(SuspiciousExpression thisSuspExpr);
}
