package jisd.fl.core.domain;

import jisd.fl.core.entity.FLRanking;
import jisd.fl.core.entity.element.LineElementName;
import jisd.fl.core.entity.sbfl.Granularity;
import jisd.fl.core.entity.susp.CauseTreeNode;
import jisd.fl.core.entity.susp.ExpressionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProbeScoreCalculatorTest {
    private FLRanking ranking;
    private static final double BASE_FACTOR = 0.8;

    @BeforeEach
    void setUp() {
        ranking = new FLRanking();
        ranking.add(new LineElementName("pkg.Class#method(int)", 10), 1.0);
        ranking.add(new LineElementName("pkg.Class#method(int)", 20), 0.8);
        ranking.add(new LineElementName("pkg.Class#method(int)", 30), 0.6);
        ranking.add(new LineElementName("pkg.Class#other()", 15), 0.5);
        ranking.sort();
    }

    @Test
    void appliesMultiplier1PlusLambda1ForDepth1Node() {
        // root -> depth1Node
        CauseTreeNode root = new CauseTreeNode(null);
        CauseTreeNode depth1Node = createNode("pkg.Class#method(int)", 10);
        root.addChildNode(depth1Node);

        ProbeScoreCalculator calc = new ProbeScoreCalculator(BASE_FACTOR, Granularity.LINE);
        calc.apply(root, ranking);

        double expected = 1.0 * (1 + Math.pow(BASE_FACTOR, 1));
        assertEquals(expected, ranking.getScore(new LineElementName("pkg.Class#method(int)", 10)), 0.0001);
    }

    @Test
    void appliesMultiplier1PlusLambda2ForDepth2Node() {
        // root -> depth1Node -> depth2Node
        CauseTreeNode root = new CauseTreeNode(null);
        CauseTreeNode depth1Node = createNode("pkg.Class#method(int)", 10);
        CauseTreeNode depth2Node = createNode("pkg.Class#method(int)", 20);
        root.addChildNode(depth1Node);
        depth1Node.addChildNode(depth2Node);

        ProbeScoreCalculator calc = new ProbeScoreCalculator(BASE_FACTOR, Granularity.LINE);
        calc.apply(root, ranking);

        double expectedDepth1 = 1.0 * (1 + Math.pow(BASE_FACTOR, 1));
        double expectedDepth2 = 0.8 * (1 + Math.pow(BASE_FACTOR, 2));
        assertEquals(expectedDepth1, ranking.getScore(new LineElementName("pkg.Class#method(int)", 10)), 0.0001);
        assertEquals(expectedDepth2, ranking.getScore(new LineElementName("pkg.Class#method(int)", 20)), 0.0001);
    }

    @Test
    void usesMinimumDepthWhenSameElementAppearsAtMultipleDepths() {
        // root -> depth1Node (line 10)
        //      -> depth1NodeB -> depth2Node (line 10 again)
        CauseTreeNode root = new CauseTreeNode(null);
        CauseTreeNode depth1Node = createNode("pkg.Class#method(int)", 10);
        CauseTreeNode depth1NodeB = createNode("pkg.Class#method(int)", 20);
        CauseTreeNode depth2NodeSameLine = createNode("pkg.Class#method(int)", 10);
        root.addChildNode(depth1Node);
        root.addChildNode(depth1NodeB);
        depth1NodeB.addChildNode(depth2NodeSameLine);

        ProbeScoreCalculator calc = new ProbeScoreCalculator(BASE_FACTOR, Granularity.LINE);
        calc.apply(root, ranking);

        // line 10 appears at depth 1 and depth 2, should use minimum (depth 1)
        double expectedMinDepth = 1.0 * (1 + Math.pow(BASE_FACTOR, 1));
        assertEquals(expectedMinDepth, ranking.getScore(new LineElementName("pkg.Class#method(int)", 10)), 0.0001);
    }

    @Test
    void excludesNodeWithNullLocationFromScoreCalculation() {
        CauseTreeNode root = new CauseTreeNode(null);
        CauseTreeNode depth1Node = createNode("pkg.Class#method(int)", 10);
        root.addChildNode(depth1Node);

        ProbeScoreCalculator calc = new ProbeScoreCalculator(BASE_FACTOR, Granularity.LINE);
        calc.apply(root, ranking);

        // rootはlocation=nullなのでスコア計算対象外
        // depth1Nodeはdepth=1（rootがスキップされるので深さは増加しない）
        double expectedDepth1 = 1.0 * (1 + Math.pow(BASE_FACTOR, 1));
        assertEquals(expectedDepth1, ranking.getScore(new LineElementName("pkg.Class#method(int)", 10)), 0.0001);
    }

    @Test
    void ignoresElementNotInRanking() {
        CauseTreeNode root = new CauseTreeNode(null);
        CauseTreeNode depth1Node = createNode("pkg.NotInRanking#method()", 100);
        root.addChildNode(depth1Node);

        ProbeScoreCalculator calc = new ProbeScoreCalculator(BASE_FACTOR, Granularity.LINE);
        // 例外が発生しないことを確認
        assertDoesNotThrow(() -> calc.apply(root, ranking));

        // 既存のスコアは変更されないこと
        assertEquals(1.0, ranking.getScore(new LineElementName("pkg.Class#method(int)", 10)), 0.0001);
    }

    private CauseTreeNode createNode(String methodFqn, int line) {
        LineElementName location = new LineElementName(methodFqn, line);
        return new CauseTreeNode(ExpressionType.ASSIGNMENT, location, "x = 1", "1");
    }
}
