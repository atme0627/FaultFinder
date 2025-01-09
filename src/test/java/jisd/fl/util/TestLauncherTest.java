package jisd.fl.util;

import jisd.debug.Debugger;
import org.junit.jupiter.api.Test;

class TestLauncherTest {

    @Test
    void launchTest(){
        String testMethodName = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint";
        //カッコつけたら動かない
        TestLauncher tl = new TestLauncher(testMethodName);
        tl.runTest();
    }

    @Test
    void jisdtest1(){
        String testMethodName = "org.apache.commons.math3.fitting.PolynomialFitterTest#testLargeSample";
        String testSrcDir = PropertyLoader.getProperty("testSrcDir");
        String targetSrcDir = PropertyLoader.getProperty("targetSrcDir");

        Debugger dbg = TestUtil.testDebuggerFactory(testMethodName);
        dbg.setSrcDir(testSrcDir, targetSrcDir);

        dbg.setMain("org.apache.commons.math3.linear.BlockRealMatrix");
        dbg.stopAt(261);
        dbg.run(5000);
        dbg.locals();
    }
}