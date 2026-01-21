package jisd.fl.core.domain;

import jisd.fl.core.domain.port.SearchSuspiciousReturnsStrategy;
import jisd.fl.infra.jdi.JDISearchSuspiciousReturnsArgumentStrategy;
import jisd.fl.infra.jdi.JDISearchSuspiciousReturnsAssignmentStrategy;
import jisd.fl.infra.jdi.JDISearchSuspiciousReturnsReturnValueStrategy;
import jisd.fl.core.entity.susp.SuspiciousArgument;
import jisd.fl.core.entity.susp.SuspiciousAssignment;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.susp.SuspiciousReturnValue;

import java.util.List;

public final class SuspiciousReturnsSearcher {
    private final SearchSuspiciousReturnsStrategy assignmentStrategy;
    private final SearchSuspiciousReturnsStrategy returnValueStrategy;
    private final SearchSuspiciousReturnsStrategy argumentStrategy;

    public SuspiciousReturnsSearcher() {
        assignmentStrategy = new JDISearchSuspiciousReturnsAssignmentStrategy();
        returnValueStrategy = new JDISearchSuspiciousReturnsReturnValueStrategy();
        argumentStrategy = new JDISearchSuspiciousReturnsArgumentStrategy();
    }

    public List<SuspiciousReturnValue> search(SuspiciousExpression suspExpr) {
        return switch (suspExpr) {
            case SuspiciousAssignment suspAssign -> assignmentStrategy.search(suspAssign);
            case SuspiciousReturnValue suspReturn -> returnValueStrategy.search(suspReturn);
            case SuspiciousArgument suspArg -> argumentStrategy.search(suspArg);
            default -> throw new IllegalArgumentException("Unknown suspicious expression type: " + suspExpr);
        };
    }
}
