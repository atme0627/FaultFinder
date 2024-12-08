package jisd.fl.util;

import com.sun.jdi.VMDisconnectedException;
import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.debug.Point;
import jisd.debug.value.ValueInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Optional;

class TestLauncherTest {

    @Test
    void launchTest(){
        String testMethodName = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint";
        //カッコつけたら動かない
        TestLauncher tl = new TestLauncher(testMethodName);
        tl.run();
    }

    @Test
    void jisdtest1(){
        String testMethodName = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint";
        String testSrcDir = PropertyLoader.getProperty("d4jTestSrcDir");

        Debugger dbg = TestUtil.testDebuggerFactory(testMethodName);
        dbg.setSrcDir(testSrcDir);
        dbg.setMain("java.jisd.fl.util.TestLauncher");
        dbg.run(1000);
    }
}