package jisd.fl.presenter;

import jisd.fl.core.entity.susp.CauseTreeNode;

public class CauseTreePresenter {
    private static final String INDENT = "    ";

    /** ツリー全体をプレーンテキストで返す */
    public static String toTreeString(CauseTreeNode root) {
        StringBuilder sb = new StringBuilder();
        formatTree(sb, root, "", true);
        return sb.toString();
    }

    /** 1階層分（親 + 直接の子）をプレーンテキストで返す */
    public static String toChildrenString(CauseTreeNode node) {
        StringBuilder sb = new StringBuilder();
        var expr = node.expression();
        sb.append("└── ").append(expr != null ? expr.toString().trim() : "(root)").append("\n");
        var children = node.children();
        for (int i = 0; i < children.size() - 1; i++) {
            var childExpr = children.get(i).expression();
            sb.append(INDENT).append("├── ").append(childExpr != null ? childExpr.toString().trim() : "(null)").append("\n");
        }
        if (!children.isEmpty()) {
            var lastExpr = children.getLast().expression();
            sb.append(INDENT).append("└── ").append(lastExpr != null ? lastExpr.toString().trim() : "(null)").append("\n");
        }
        return sb.toString();
    }

    private static void formatTree(StringBuilder sb, CauseTreeNode node,
                                   String prefix, boolean isTail) {
        var expr = node.expression();
        sb.append(prefix).append(isTail ? "└── " : "├── ")
                .append(expr != null ? expr.toString().trim() : "(root)").append("\n");

        var children = node.children();
        for (int i = 0; i < children.size() - 1; i++) {
            formatTree(sb, children.get(i), prefix + (isTail ? INDENT : "│   "), false);
        }
        if (!children.isEmpty()) {
            formatTree(sb, children.getLast(), prefix + (isTail ? INDENT : "│   "), true);
        }
    }
}
