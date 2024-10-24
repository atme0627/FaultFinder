package jisd.fl.util;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TestClassCompiler {
    private final String junitConsoleLauncherPath = PropertyLoader.getProperty("junitConsoleLauncherPath");
    private final String compiledWithJunitFilePath = PropertyLoader.getProperty("compiledWithJunitFilePath");

    public TestClassCompiler() {
    }

    public void compileTestClass(String TestSrcPath, String mainBinDir) {

        DirectoryUtil.initDirectory(compiledWithJunitFilePath);

        String[] args = {"-cp", junitConsoleLauncherPath + ":" + mainBinDir, " -d ", compiledWithJunitFilePath, TestSrcPath};
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        System.out.println("javac " + args);
        int rc = javac.run(null, null, null, args);
        if (rc != 0) {
            throw new RuntimeException("failed to compile.");
        }
    }
}