package jisd.fl.probe.info;

import java.util.List;

/**
 * A base class for visiting nodes in a SuspiciousExpression tree.
 * It handles the recursive traversal (pre-order) and tracks the depth of each node.
 *
 * To use it, create a subclass and implement the abstract `visit` method.
 *
 * Example:
 * <pre>
 * {@code
 * public class MyVisitor extends SuspiciousExpressionVisitor {
 *     @Override
 *     public void visit(SuspiciousExpression node, int depth) {
 *         // Implement your logic here
 *         System.out.println("Depth: " + depth + ", Node: " + node);
 *     }
 * }
 *
 * // To run the visitor
 * SuspiciousExpression rootNode = ...; // get the root of the tree
 * MyVisitor visitor = new MyVisitor();
 * visitor.start(rootNode);
 * }
 * </pre>
 */
public abstract class SuspiciousExpressionVisitor {

    /**
     * The action to be performed on each visited node.
     *
     * @param node  The SuspiciousExpression node currently being visited.
     * @param depth The depth of the node from the root (root is at depth 0).
     */
    public abstract void visit(SuspiciousExpression node, int depth);

    /**
     * Starts the traversal from the given root node.
     *
     * @param root The root node of the SuspiciousExpression tree.
     */
    public void start(SuspiciousExpression root) {
        if (root != null) {
            traverse(root, 0);
        }
    }

    /**
     * Performs a pre-order traversal of the tree.
     * It first visits the current node, then recursively visits its children.
     *
     * @param node  The current node.
     * @param depth The current depth.
     */
    private void traverse(SuspiciousExpression node, int depth) {
        // Perform the action on the current node
        visit(node, depth);

        // Recurse on children.
        // The field 'childSuspExprs' in SuspiciousExpression has package-private access,
        // so this visitor in the same package can access it.
        List<SuspiciousExpression> children = node.childSuspExprs;
        for (SuspiciousExpression child : children) {
            traverse(child, depth + 1);
        }
    }
}
