package jisd.fl.core.domain.port;

import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.susp.SuspiciousReturnValue;

import java.util.List;

public interface SearchSuspiciousReturnsStrategy {
    public List<SuspiciousReturnValue> search(SuspiciousExpression suspExpr);
}
