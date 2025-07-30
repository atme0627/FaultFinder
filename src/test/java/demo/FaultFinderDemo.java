package demo;
import jisd.fl.FaultFinder;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.MethodElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FaultFinderDemo {
    MethodElementName failedTestMethodName = new MethodElementName("demo.SampleTest#sampleTest()");
    FaultFinder faultFinder;

    @BeforeEach
    void init(){
        TestUtil.compileForDebug(failedTestMethodName);
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
        faultFinder.printRanking();
    }
}
