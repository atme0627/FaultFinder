package demo;
import jisd.fl.FaultFinder;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.MethodElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FaultFinderDemo {
    MethodElementName failedTestMethodName = new MethodElementName("demo.SampleTest#sampleTest()");
    @BeforeEach
    void init(){
        TestUtil.compileForDebug(failedTestMethodName);
    }

    @Test
    void example(){
        //既存のバグ局所化手法、SBFLに基づいた「怪しい行」ランキング
        //テストケースが乏しいため、初めは全て同率になってしまう
        FaultFinder faultFinder = new FaultFinder(failedTestMethodName);
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
