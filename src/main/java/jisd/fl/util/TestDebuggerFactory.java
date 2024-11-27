package jisd.fl.util;

import jisd.debug.Debugger;
import jisd.fl.util.TestLauncher;

public class TestDebuggerFactory {

    public TestDebuggerFactory() {
    }

    public Debugger create(String testClassName, String testMethodName) {
        String testBinDir = PropertyLoader.getProperty("d4jTestBinDir");
        String targetBinDir = PropertyLoader.getProperty("d4jTargetBinDir");
        String junitClassPath = PropertyLoader.getJunitClassPaths();
        Debugger dbg = new Debugger("jisd.fl.util.TestLauncher " + testClassName + " " + testMethodName,
                "-cp " + "./build/classes/java/main" + ":" + testBinDir + ":" + targetBinDir + ":" + junitClassPath);
        dbg.setMain(testClassName);
        return dbg;
    }
}