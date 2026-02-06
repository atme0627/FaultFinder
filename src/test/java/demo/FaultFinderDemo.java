package demo;
import jisd.fl.FaultFinder;
import jisd.fl.core.entity.sbfl.Granularity;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.presenter.SbflCoveragePrinter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

@Tag("demo")
public class FaultFinderDemo {
    MethodElementName failedTestMethodName = new MethodElementName("demo.SampleTest#sampleTest()");
    FaultFinder faultFinder;

    @BeforeEach
    void init(){
        PropertyLoader.ProjectConfig config = new PropertyLoader.ProjectConfig(
                Path.of("/Users/ezaki/IdeaProjects/FaultFinder"),
                Path.of("src/main/java"),
                Path.of("src/test/java"),
                Path.of("build/classes/java/main"),
                Path.of("build/classes/java/test")
        );
        PropertyLoader.setProjectConfig(config);
        faultFinder = new FaultFinder(failedTestMethodName.classElementName);
    }

    @Test
    void displaySBFLRanking(){
        faultFinder.printRanking();
    }

    @Test
    void probe(){
        SuspiciousLocalVariable targetValue = new SuspiciousLocalVariable(
                failedTestMethodName,
                failedTestMethodName,
                "actual",
                "4",
                true
        );

        SbflCoveragePrinter printer = new SbflCoveragePrinter();
        printer.print(faultFinder.coverage, Granularity.LINE);
        faultFinder.probe(targetValue);
    }

    @Test
    void useCase(){
        //既存のバグ局所化手法、SBFLに基づいた「怪しい行」ランキング
        //テストケースが乏しいため、初めは全て同率になってしまう
        faultFinder.printRanking();

        //間違った値を取る変数"actual"をヒントとしてランキングに与えることで、疑惑値に差が生まれ調べるべき行が絞られる。
        SuspiciousLocalVariable targetValue = new SuspiciousLocalVariable(
                failedTestMethodName,
                failedTestMethodName,
                "actual",
                "4",
                true
        );
        faultFinder.probe(targetValue);

        //改善されたランキングの1位を調べた結果、methodCalling(..)メソッドにはバグは含まれていない
        faultFinder.remove(1);

        //add(...)を調べた結果バグが見つかる
    }

    @Test
    void remove(){
        faultFinder.printRanking(10);
        faultFinder.remove(1);
    }

    @Test
    void susp(){
        faultFinder.printRanking(10);
        faultFinder.susp(1);
    }
}
