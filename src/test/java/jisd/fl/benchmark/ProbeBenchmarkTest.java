package jisd.fl.benchmark;

import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.CauseTreeNode;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.usecase.Probe;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Probe.run() のベンチマーク。
 * BFS 探索全体の実行時間を計測し、高速化の効果測定に使用する。
 *
 * 5つの異なる「極端さ」の軸で探索性能を評価:
 * 1. 深さ極端: call stack の深さ（20段ネスト）
 * 2. 繰り返し極端: 同一メソッドの重複呼び出し（再帰100回）
 * 3. 分岐極端: 指数的な探索空間（2^10 = 1024 nodes）
 * 4. 動的解決極端: ポリモーフィズム解決（50種類の実装）
 * 5. 現実的ケース: 複数クラスを跨ぐ呼び出しチェーン
 */
@Tag("benchmark")
@Execution(ExecutionMode.SAME_THREAD)
class ProbeBenchmarkTest {

    private static final Logger logger = LoggerFactory.getLogger(ProbeBenchmarkTest.class);
    private static final String FIXTURE_FQCN = "jisd.fixture.ProbeBenchmarkFixture";

    private static PropertyLoader.ProjectConfig original;

    @BeforeAll
    static void setUpProjectConfigForFixtures() {
        original = PropertyLoader.getTargetProjectConfig();

        var cfg = new PropertyLoader.ProjectConfig(
                Path.of("/Users/ezaki/IdeaProjects/FaultFinder/src/test/resources/fixtures"),
                Path.of("exec/src/main/java"),
                Path.of("exec/src/main/java"),
                Path.of("/Users/ezaki/IdeaProjects/FaultFinder/build/classes/java/fixtureExec"),
                Path.of("/Users/ezaki/IdeaProjects/FaultFinder/build/classes/java/fixtureExec")
        );

        PropertyLoader.setProjectConfig(cfg);
    }

    @AfterAll
    static void restoreProjectConfig() {
        if (original != null) {
            PropertyLoader.setProjectConfig(original);
        }
    }

    // =========================================================================
    // 1. 深さ極端: 20段のネスト
    // =========================================================================

    @Test
    @Timeout(120)
    void bench_depth_extreme() {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#bench_depth_extreme()");

        // 1 + 20 = 21
        CauseTreeNode result = runProbeWithTiming(testMethod, "x", "21", "depth (20 levels)");
        logTreeSize(result, "depth");
    }

    // =========================================================================
    // 2. 繰り返し極端: 再帰で同一メソッドが100回呼ばれる
    // =========================================================================

    @Test
    @Timeout(300)  // 繰り返し極端は約3.5分かかる
    void bench_repetition_extreme() {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#bench_repetition_extreme()");

        // 1+2+...+100 = 5050 (ループで同一メソッドを100回呼び出し)
        CauseTreeNode result = runProbeWithTiming(testMethod, "sum", "5050", "repetition (100 loop calls)");
        logTreeSize(result, "repetition");
    }

    // =========================================================================
    // 3. 分岐極端: 2分岐 × depth=10 → 1024 nodes
    // =========================================================================

    @Test
    @Timeout(900)  // 分岐極端は探索空間が大きいため、15分
    void bench_branch_extreme() {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#bench_branch_extreme()");

        // branch10(1) = 6144
        CauseTreeNode result = runProbeWithTiming(testMethod, "x", "6144", "branch (1024 nodes)");
        logTreeSize(result, "branch");
    }

    // =========================================================================
    // 4. 動的解決極端: 50種類の実装クラスをループで呼ぶ
    // =========================================================================

    @Test
    @Timeout(120)
    void bench_polymorphism_extreme() {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#bench_polymorphism_extreme()");

        // 1+2+...+50 = 1275
        CauseTreeNode result = runProbeWithTiming(testMethod, "total", "1275", "polymorphism (50 implementations)");
        logTreeSize(result, "polymorphism");
    }

    // =========================================================================
    // 5. 現実的ケース: 複数クラスを跨ぐ呼び出しチェーン
    // =========================================================================

    @Test
    @Timeout(120)
    void bench_realistic_multi_class() {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#bench_realistic_multi_class()");

        // processOrder(100, 5) の結果
        // basePrice = 100 * 10 = 1000
        // subtotal = 1000 * 5 = 5000
        // discounted = 5000 (quantity < 10)
        // taxed = 5000 * 110 / 100 = 5500
        CauseTreeNode result = runProbeWithTiming(testMethod, "result", "5500", "realistic (multi-method chain)");
        logTreeSize(result, "realistic");
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private static CauseTreeNode runProbeWithTiming(
            MethodElementName testMethod, String variableName, String actualValue, String label) {

        SuspiciousLocalVariable target = new SuspiciousLocalVariable(
                testMethod, testMethod, variableName, actualValue, true);

        Probe probe = new Probe(target);

        long start = System.nanoTime();
        CauseTreeNode result = probe.run(0);
        long elapsed = System.nanoTime() - start;

        printBench(label, elapsed);
        return result;
    }

    private static void logTreeSize(CauseTreeNode root, String label) {
        int nodeCount = countNodes(root);
        logger.info("[BENCH] {}/tree_size : {} nodes", label, nodeCount);
    }

    private static int countNodes(CauseTreeNode node) {
        if (node == null) return 0;
        int count = 1;
        for (CauseTreeNode child : node.children()) {
            count += countNodes(child);
        }
        return count;
    }

    private static void printBench(String label, long elapsedNanos) {
        logger.info("[BENCH] {} : {} ms", label, elapsedNanos / 1_000_000);
    }
}
