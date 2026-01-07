package jisd.fl.probe.info;

import java.util.ArrayList;
import java.util.List;

public class SuspiciousExprTreeNode {
    public final SuspiciousExpression suspExpr;
    public final List<SuspiciousExprTreeNode> childSuspExprs = new ArrayList<>();
    public SuspiciousExprTreeNode(SuspiciousExpression suspExpr) {
        this.suspExpr = suspExpr;
    }

    public void addChild(SuspiciousExpression ch){
        this.childSuspExprs.add(new SuspiciousExprTreeNode(ch));
    }

    public void addChild(List<? extends SuspiciousExpression> chs){
        for(SuspiciousExpression ch : chs) this.addChild(ch);
    }

    public SuspiciousExprTreeNode find(SuspiciousExpression target){
        if(suspExpr.equals(target)) return this;
        for(SuspiciousExprTreeNode child : childSuspExprs){
            SuspiciousExprTreeNode found = child.find(target);
            if(found != null) return found;
        }
        return null;
    }
}
