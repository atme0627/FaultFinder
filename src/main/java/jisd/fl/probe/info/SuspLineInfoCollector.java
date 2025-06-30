package jisd.fl.probe.info;

import java.util.ArrayList;
import java.util.List;

/**
 * SuspiciousExpressionの木構造を走査し、故障箇所特定に必要な情報（CodeElementName、行番号、深さ）を収集するクラス。
 * <p>
 * このクラスはビジターパターンと同様の目的を果たしますが、既存の {@link SuspiciousExpression} クラス階層に
 * acceptメソッドを追加する必要がないように、外部から木構造をトラバースします。
 * </p>
 */
public class SuspLineInfoCollector {

    private final List<SuspLineOccurrence> visitedNodes = new ArrayList<>();

    /**
     * 指定されたSuspiciousExpressionのルートノードから木構造の走査を開始し、
     * 各ノードのCodeElementName、行番号、および木の深さを収集します。
     *
     * @param root 走査を開始する木構造のルートノード。
     * @return 収集されたすべてのノード情報のリスト。リストは走査順（深さ優先）にソートされています。
     */
    public List<SuspLineOccurrence> collect(SuspiciousExpression root) {
        visitedNodes.clear();
        collectRecursive(root, 0);
        return new ArrayList<>(visitedNodes); // 防御的コピーを返す
    }

    /**
     * 再帰的に木構造を深さ優先で走査します。
     *
     * @param node  現在訪れているノード。
     * @param depth 現在のノードの深さ。
     */
    private void collectRecursive(SuspiciousExpression node, int depth) {
        if (node == null) {
            return;
        }

        // 現在のノードの情報を収集
        SuspLineOccurrence info = new SuspLineOccurrence(node.locateMethod, node.locateLine, depth);
        visitedNodes.add(info);

        // 子ノードを再帰的に走査
        if (node.childSuspExprs != null) {
            for (SuspiciousExpression child : node.childSuspExprs) {
                collectRecursive(child, depth + 1);
            }
        }
    }
}
