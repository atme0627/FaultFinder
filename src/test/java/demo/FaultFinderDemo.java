package demo;
import jisd.fl.FaultFinder;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestUtil;
import jisd.fl.core.entity.MethodElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FaultFinderDemo {
    MethodElementName failedTestMethodName = new MethodElementName("demo.SampleTest#sampleTest()");
    FaultFinder faultFinder;

    @BeforeEach
    void init(){
        PropertyLoader.setTargetSrcDir("/Users/ezaki/IdeaProjects/MyFaultFinder/src/main/java");
        PropertyLoader.setTargetBinDir("/Users/ezaki/IdeaProjects/MyFaultFinder/build/classes/java/main");
        PropertyLoader.setTestSrcDir("/Users/ezaki/IdeaProjects/MyFaultFinder/src/test/java");
        PropertyLoader.setTestBinDir("/Users/ezaki/IdeaProjects/MyFaultFinder/build/classes/java/test");
        PropertyLoader.store();
    }
    @BeforeEach
    void initFaultFinder(){
        faultFinder = new FaultFinder(failedTestMethodName);
    }

    @Test
    void displaySBFLRanking(){
        faultFinder.printRanking();
    }

    @Test
    void probe(){
        SuspiciousVariable targetValue = new SuspiciousVariable(
                failedTestMethodName,
                failedTestMethodName.getFullyQualifiedMethodName(),
                "actual",
                "4",
                true,
                false
        );
        faultFinder.probe(targetValue);
    }

    @Test
    void useCase(){
        //既存のバグ局所化手法、SBFLに基づいた「怪しい行」ランキング
        //テストケースが乏しいため、初めは全て同率になってしまう
        faultFinder.printRanking();

        //間違った値を取る変数"actual"をヒントとしてランキングに与えることで、疑惑値に差が生まれ調べるべき行が絞られる。
        SuspiciousVariable targetValue = new SuspiciousVariable(
                failedTestMethodName,
                failedTestMethodName.getFullyQualifiedMethodName(),
                "actual",
                "4",
                true,
                false
        );
        faultFinder.probe(targetValue);

        //改善されたランキングの1位を調べた結果、methodCalling(..)メソッドにはバグは含まれていない
        faultFinder.remove(1);

        //add(...)を調べた結果バグが見つかる
    }
}
