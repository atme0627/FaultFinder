package jisd.fl.infra.jdi;

import jisd.fl.core.domain.port.SearchSuspiciousReturnsStrategy;
import jisd.fl.probe.info.*;

import java.util.List;

public class JDISearchSuspiciousReturnsArgumentStrategy implements SearchSuspiciousReturnsStrategy {
    @Override
    public List<SuspiciousReturnValue> search(SuspiciousExpression suspExpr) {
        return JDISuspArg.searchSuspiciousReturns((SuspiciousArgument) suspExpr);
    }
}
