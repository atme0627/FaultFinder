package jisd.fl.util;

import com.sun.jdi.VMDisconnectedException;
import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.debug.Point;
import jisd.debug.value.ValueInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class TestLauncherTest {
    @Test
    void launchTest(){
        String testClassName = "demo.SampleTest";
        String testMethodName = "sample2";
        //カッコつけたら動かない
        TestLauncher tl = new TestLauncher(testClassName, testMethodName);
        tl.run();
    }

    @Test
    void jisdtest1(){
        String testClassName = "demo.SampleTest";
        String testMethodName = "sample2";
        String testSrcDir = PropertyLoader.getProperty("testSrcDir");

        TestDebuggerFactory td = new TestDebuggerFactory();
        Debugger dbg = td.create(testClassName, testMethodName);
        dbg.setSrcDir(testSrcDir);
        dbg.setMain("demo.SampleTest");
        Optional<Point> p = dbg.stopAt(25);
        dbg.run(1000);
        dbg.locals();

    }

    @Test
    void jisdtest2(){
        String testClassName = "demo.SampleTest";
        String testMethodName = "sample2";
        String testSrcDir = PropertyLoader.getProperty("testSrcDir");

        TestDebuggerFactory td = new TestDebuggerFactory();
        Debugger dbg = td.create(testClassName, testMethodName);
        dbg.setSrcDir(testSrcDir);
        dbg.setMain("demo.SampleTest");
        Optional<Point> p = dbg.watch(25);
        try {
            dbg.run(1000);
        }
        catch (VMDisconnectedException e){
        }
        dbg.exit();
        DebugResult dr = p.get().getResults("c").get();
        ArrayList<ValueInfo> vis = dr.getValues();
        for(ValueInfo vi : vis){
            System.out.println(vi.getValue());
        }
    }


}