package jisd.fl.probe.info;

import jisd.fl.probe.record.TracedValueCollection;

public class JDISuspExpr {
    /**
     * このSuspiciousExprで観測できる全ての変数とその値の情報をJISDを用いて取得
     * 複数回SuspiciousExpressionが実行されているときは、最後に実行された時の値を使用する
     * @return
     */
    public static TracedValueCollection traceAllValuesAtSuspExpr(SuspiciousExpression thisSuspExpr){
        return switch (thisSuspExpr) {
            case SuspiciousAssignment thisSuspAssign ->
                    JDISuspAssign.traceAllValuesAtSuspExpr(thisSuspAssign);
            case SuspiciousReturnValue thisSuspReturn ->
                    JDISuspReturn.traceAllValuesAtSuspExpr(thisSuspReturn);
            case SuspiciousArgument thisSuspArg -> JDISuspArg.traceAllValuesAtSuspExpr(thisSuspArg);
            default -> throw new IllegalStateException("Unexpected value: " + thisSuspExpr);
        };
    }

}
