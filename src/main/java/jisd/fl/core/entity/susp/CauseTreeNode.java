package jisd.fl.core.entity.susp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CauseTreeNode {
    private final SuspiciousExpression expression;
    private final List<CauseTreeNode> children = new ArrayList<>();

    public CauseTreeNode(SuspiciousExpression expression) {
        this.expression = expression;
    }

    public SuspiciousExpression expression() {
        return expression;
    }

    public List<CauseTreeNode> children() {
        return Collections.unmodifiableList(children);
    }

    public void addChild(SuspiciousExpression ch) {
        this.children.add(new CauseTreeNode(ch));
    }

    public void addChild(List<? extends SuspiciousExpression> chs) {
        for (SuspiciousExpression ch : chs) this.addChild(ch);
    }

    public CauseTreeNode find(SuspiciousExpression target) {
        if (expression.equals(target)) return this;
        for (CauseTreeNode child : children) {
            CauseTreeNode found = child.find(target);
            if (found != null) return found;
        }
        return null;
    }

    @Override
    public String toString() {
        return expression != null ? expression.toString() : "(root)";
    }
}
