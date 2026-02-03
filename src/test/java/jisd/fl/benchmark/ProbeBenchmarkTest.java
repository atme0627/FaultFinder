package jisd.fl.benchmark;

import jisd.fl.FaultFinder;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.core.util.PropertyLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Probe.run() のベンチマーク。
 * BFS 探索全体の実行時間を計測し、高速化の効果測定に使用する。
 */
class ProbeBenchmarkTest {

    private static final Logger logger = LoggerFactory.getLogger(ProbeBenchmarkTest.class);

    /**
     * FaultFinderDemo#probe() と同等のシナリオ。
     * FaultFinder の初期化（SBFL カバレッジ計算）+ Probe.run() の全体時間を計測。
     */
    @Test
    @Timeout(120)
    void bench_faultfinder_demo_probe() {
        PropertyLoader.ProjectConfig config = new PropertyLoader.ProjectConfig(
                Path.of("/Users/ezaki/IdeaProjects/FaultFinder"),
                Path.of("src/main/java"),
                Path.of("src/test/java"),
                Path.of("build/classes/java/main"),
                Path.of("build/classes/java/test")
        );
        PropertyLoader.setProjectConfig(config);

        MethodElementName failedTest = new MethodElementName("demo.SampleTest#sampleTest()");

        long initStart = System.nanoTime();
        FaultFinder faultFinder = new FaultFinder(failedTest.classElementName);
        long initElapsed = System.nanoTime() - initStart;
        printBench("FaultFinder/init (coverage)", initElapsed);

        SuspiciousLocalVariable target = new SuspiciousLocalVariable(
                failedTest,
                failedTest,
                "actual",
                "4",
                true
        );

        long probeStart = System.nanoTime();
        faultFinder.probe(target);
        long probeElapsed = System.nanoTime() - probeStart;
        printBench("FaultFinder/probe (demo)", probeElapsed);
        printBench("FaultFinder/total (demo)", initElapsed + probeElapsed);
    }

    private static void printBench(String label, long elapsedNanos) {
        logger.info("[BENCH] {} : {} ms", label, elapsedNanos / 1_000_000);
    }
}
