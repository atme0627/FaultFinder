package jisd.fl.core.domain.port;

import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousReturnValue;

import java.util.List;

public interface SearchSuspiciousReturnsStrategy {
    public List<SuspiciousReturnValue> search(SuspiciousExpression suspExpr);
}
