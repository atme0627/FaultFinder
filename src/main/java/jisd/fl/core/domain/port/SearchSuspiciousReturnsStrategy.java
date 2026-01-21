package jisd.fl.core.domain.port;

import jisd.fl.core.entity.susp.SuspiciousExpression;

import java.util.List;

public interface SearchSuspiciousReturnsStrategy {
    List<SuspiciousExpression> search(SuspiciousExpression suspExpr);
}
