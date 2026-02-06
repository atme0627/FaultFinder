package jisd.fl.core.entity.susp;

/**
 * SuspiciousExpression の種類を表す列挙型。
 */
public enum ExpressionType {
    ASSIGNMENT,
    RETURN,
    ARGUMENT;

    /**
     * SuspiciousExpression から ExpressionType を取得する。
     */
    public static ExpressionType from(SuspiciousExpression expr) {
        return switch (expr) {
            case SuspiciousAssignment _ -> ASSIGNMENT;
            case SuspiciousReturnValue _ -> RETURN;
            case SuspiciousArgument _ -> ARGUMENT;
        };
    }
}
