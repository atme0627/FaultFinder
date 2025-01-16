package jisd.fl.util;

import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        String testMethodName = "org.apache.commons.math.stat.regression.SimpleRegressionTest#testSSENonNegative()";
        String testSrcDir = PropertyLoader.getProperty("testSrcDir");
        String targetSrcDir = PropertyLoader.getProperty("targetSrcDir");

        Debugger dbg = TestUtil.testDebuggerFactory(testMethodName);
        dbg.setSrcDir(testSrcDir, targetSrcDir);

        dbg.setMain("org.apache.commons.math.stat.regression.SimpleRegressionTest");
        dbg.stopAt(275);
        dbg.run(5000);
        dbg.step(2);
        dbg.locals();
        List<DebugResult> drs = dbg.getResults();

        for(DebugResult dr : drs){
            System.out.println(dr.getLocation().getVarName() + ": " + dr.lv());
        }

    }
}