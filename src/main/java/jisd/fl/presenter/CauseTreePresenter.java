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
        sb.append("└── ").append(node.toString().trim()).append("\n");
        var children = node.children();
        for (int i = 0; i < children.size() - 1; i++) {
            sb.append(INDENT).append("├── ").append(children.get(i).toString().trim()).append("\n");
        }
        if (!children.isEmpty()) {
            sb.append(INDENT).append("└── ").append(children.getLast().toString().trim()).append("\n");
        }
        return sb.toString();
    }

    private static void formatTree(StringBuilder sb, CauseTreeNode node,
                                   String prefix, boolean isTail) {
        sb.append(prefix).append(isTail ? "└── " : "├── ")
                .append(node.toString().trim()).append("\n");

        var children = node.children();
        for (int i = 0; i < children.size() - 1; i++) {
            formatTree(sb, children.get(i), prefix + (isTail ? INDENT : "│   "), false);
        }
        if (!children.isEmpty()) {
            formatTree(sb, children.getLast(), prefix + (isTail ? INDENT : "│   "), true);
        }
    }
}
