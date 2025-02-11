package jisd.fl.util;

import com.sun.jdi.*;
import experiment.defect4j.Defects4jUtil;
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
        String testMethodName = "org.apache.commons.math3.fraction.BigFractionTest#testDigitLimitConstructor()";
        String testSrcDir = PropertyLoader.getProperty("testSrcDir");
        String targetSrcDir = PropertyLoader.getProperty("targetSrcDir");

        Debugger dbg = TestUtil.testDebuggerFactory(testMethodName);
        dbg.setSrcDir(testSrcDir, targetSrcDir);

        dbg.setMain("org.apache.commons.math3.fraction.BigFraction");
        dbg.stopAt(274);
        dbg.stopAt(283);
        dbg.stopAt(284);
        dbg.stopAt(300);
        dbg.run(3000);
        ThreadReference th = dbg.thread();
        for(int i = 0; i < 4; i++) {
            dbg.step();
            try {
                List<StackFrame> st = th.frames();
                Location loc = st.get(0).location();
                System.out.println(loc.method().toString());
            } catch (IncompatibleThreadStateException e) {
                throw new RuntimeException(e);
            }
            dbg.cont(10);
        }
    }

    @Test
    void launchTest2(){
        String project = "Math";
        int bugId = 22;

        Defects4jUtil.changeTargetVersion(project, bugId);
        String testMethodName = "org.apache.commons.math3.distribution.FDistributionTest#testIsSupportLowerBoundInclusive()";
        //カッコつけたら動かない
        TestLauncher tl = new TestLauncher(testMethodName);
        tl.runTest();
    }
}