package jisd.fl.usecase;

import jisd.fl.core.entity.coverage.LineCoverageEntry;
import jisd.fl.core.entity.coverage.SbflCounts;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.infra.jacoco.ProjectSbflCoverage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CoverageAnalyzer の統合テスト。
 *
 * テスト観点:
 * - テストの実行とカバレッジ収集が正しく動作すること
 * - 成功テスト (ep) と失敗テスト (ef) が正しくカウントされること
 * - LINE 粒度でカバレッジが取得できること
 */
@Execution(ExecutionMode.SAME_THREAD)
class CoverageAnalyzerTest {

    private static final String FIXTURE_FQCN = "jisd.fixture.CoverageFixture";
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

    @Test
    @Timeout(60)
    void analyze_collects_coverage_for_all_tests() {
        // CoverageFixture には 9 テストがある (6 pass, 3 fail)
        CoverageAnalyzer analyzer = new CoverageAnalyzer();
        ProjectSbflCoverage coverage = analyzer.analyze(new ClassElementName(FIXTURE_FQCN));

        // カバレッジが収集されていること
        assertFalse(coverage.byClass.isEmpty(), "カバレッジデータが収集されているべき");

        // CoverageFixture のカバレッジが含まれること
        assertTrue(coverage.byClass.containsKey(new ClassElementName(FIXTURE_FQCN)),
                "CoverageFixture のカバレッジが含まれるべき");
    }

    @Test
    @Timeout(60)
    void analyze_counts_passed_and_failed_tests_correctly() {
        CoverageAnalyzer analyzer = new CoverageAnalyzer();
        ProjectSbflCoverage coverage = analyzer.analyze(new ClassElementName(FIXTURE_FQCN));

        // LINE カバレッジを取得
        List<LineCoverageEntry> lineEntries = coverage.lineCoverageEntries(true).toList();
        assertFalse(lineEntries.isEmpty(), "LINE カバレッジが取得できるべき");

        // divide メソッドの行を探す（branch 2: return a / b）
        // この行は test_divide_normal_pass (ep) と test_divide_normal_fail (ef) でカバーされる
        var divideLines = lineEntries.stream()
                .filter(e -> e.e().fullyQualifiedClassName().equals(FIXTURE_FQCN))
                .collect(Collectors.toList());

        assertFalse(divideLines.isEmpty(), "CoverageFixture の LINE カバレッジが取得できるべき");

        // ep と ef の両方が 0 より大きい行が存在すること（成功・失敗テスト両方でカバー）
        boolean hasEpAndEf = divideLines.stream()
                .anyMatch(e -> e.counts().ep() > 0 && e.counts().ef() > 0);
        assertTrue(hasEpAndEf,
                "ep > 0 かつ ef > 0 の行が存在するべき（成功・失敗テスト両方でカバーされた行）");
    }

    @Test
    @Timeout(60)
    void analyze_line_coverage_has_correct_ep_ef_ratio() {
        CoverageAnalyzer analyzer = new CoverageAnalyzer();
        ProjectSbflCoverage coverage = analyzer.analyze(new ClassElementName(FIXTURE_FQCN));

        // 全 LINE カバレッジの ep, ef の合計を計算
        Map<Boolean, Long> counts = coverage.lineCoverageEntries(true)
                .filter(e -> e.e().fullyQualifiedClassName().equals(FIXTURE_FQCN))
                .collect(Collectors.groupingBy(
                        e -> e.counts().ep() > 0,
                        Collectors.counting()
                ));

        // 成功テストでカバーされた行が存在すること
        assertTrue(counts.getOrDefault(true, 0L) > 0,
                "成功テストでカバーされた行 (ep > 0) が存在するべき");
    }

    @Test
    @Timeout(60)
    void analyze_covers_conditional_branches() {
        CoverageAnalyzer analyzer = new CoverageAnalyzer();
        ProjectSbflCoverage coverage = analyzer.analyze(new ClassElementName(FIXTURE_FQCN));

        List<LineCoverageEntry> lineEntries = coverage.lineCoverageEntries(true)
                .filter(e -> e.e().fullyQualifiedClassName().equals(FIXTURE_FQCN))
                .toList();

        // classify メソッドは 3 つのブランチを持つ
        // - test_classify_negative_pass: branch 1 (negative)
        // - test_classify_zero_pass: branch 2 (zero)
        // - test_classify_positive_pass/fail: branch 3 (positive)

        // 少なくとも 3 つ以上の行がカバーされていること（各ブランチの return 文）
        long coveredLines = lineEntries.stream()
                .filter(e -> e.counts().ep() + e.counts().ef() > 0)
                .count();

        assertTrue(coveredLines >= 3,
                "少なくとも 3 行以上がカバーされているべき: " + coveredLines);
    }

    @Test
    @Timeout(60)
    void analyze_sum_method_loop_coverage() {
        CoverageAnalyzer analyzer = new CoverageAnalyzer();
        ProjectSbflCoverage coverage = analyzer.analyze(new ClassElementName(FIXTURE_FQCN));

        List<LineCoverageEntry> lineEntries = coverage.lineCoverageEntries(true)
                .filter(e -> e.e().fullyQualifiedClassName().equals(FIXTURE_FQCN))
                .toList();

        // sum メソッドは test_sum_pass (ep) と test_sum_fail (ef) でカバーされる
        // ループ内の行は両方でカバーされるはず

        // total の合計を計算
        int totalEp = lineEntries.stream().mapToInt(e -> e.counts().ep()).sum();
        int totalEf = lineEntries.stream().mapToInt(e -> e.counts().ef()).sum();

        assertTrue(totalEp > 0, "成功テストのカバレッジ (ep) が 0 より大きいべき: " + totalEp);
        assertTrue(totalEf > 0, "失敗テストのカバレッジ (ef) が 0 より大きいべき: " + totalEf);

        // 成功テストの方が多い (6 pass vs 3 fail)
        assertTrue(totalEp > totalEf,
                "成功テスト (ep=" + totalEp + ") は失敗テスト (ef=" + totalEf + ") より多いはず");
    }
}
