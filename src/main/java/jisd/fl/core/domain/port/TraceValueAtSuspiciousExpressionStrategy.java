package jisd.fl.core.domain.port;

import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.TracedValue;

import java.util.List;

public interface TraceValueAtSuspiciousExpressionStrategy {
    List<TracedValue> traceAllValuesAtSuspExpr(SuspiciousExpression thisSuspExpr);
}
