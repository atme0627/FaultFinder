package jisd.fl.core.domain.port;

import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.probe.record.TracedValueCollection;

public interface TraceValueAtSuspiciousExpressionStrategy {
    TracedValueCollection traceAllValuesAtSuspExpr(SuspiciousExpression thisSuspExpr);
}
