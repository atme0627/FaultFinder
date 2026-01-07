package jisd.fl.probe.info;

import java.util.ArrayList;
import java.util.List;

public class SuspiciousExprTreeNode {
    private static final String INDENT = "    ";
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

    public void print() {
        StringBuilder sb = new StringBuilder();
        printTree(sb, "", true);
        System.out.print(sb);
    }

    private void printTree(StringBuilder sb, String prefix, boolean isTail) {
        sb.append(prefix).append(isTail ? "└── " : "├── ").append(suspExpr.toString().trim()).append("\n");

        for (int i = 0; i < childSuspExprs.size() - 1; i++) {
            childSuspExprs.get(i).printTree(sb, prefix + (isTail ? INDENT : "│   "), false);
        }
        if (!childSuspExprs.isEmpty()) {
            childSuspExprs.get(childSuspExprs.size() - 1)
                    .printTree(sb, prefix + (isTail ? INDENT : "│   "), true);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        printTree(sb, "", true);
        return sb.toString();
    }

}
