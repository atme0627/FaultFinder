package jisd.fl.core.domain.port;

import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.record.TracedValueCollection;

public interface ValueTracer {
    public TracedValueCollection traceAllAtSuspiciousExpression(SuspiciousExpression SuspExpr);
}
