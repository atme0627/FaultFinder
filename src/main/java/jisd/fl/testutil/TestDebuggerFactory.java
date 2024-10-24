package jisd.fl.testutil;

import jisd.debug.Debugger;

public class TestDebuggerFactory {
    private final TestClassCompiler tcc = new TestClassCompiler();
    public TestDebuggerFactory() {
    }

    public Debugger create(String testClassName, String testMethodName, String testJavaFilePath, String mainBinDir, String junitStandaloneDir) {
        tcc.compileTestClass(testJavaFilePath, mainBinDir);
        String main = "-jar " + junitStandaloneDir + "/" + tcc.getJunitStandaloneName() + " -cp " + tcc.getTestBinDir() + " --select-method=" + testClassName + "#" + testMethodName;
        String option = "-cp " + tcc.getTestBinDir();
        return new Debugger(main, option);
    }
}