package jisd.fl.testutil;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TestClassCompiler {
    private String junitStandaloneDir = "./locallib";
    private String junitStandaloneName = "junit-platform-console-standalone-1.10.0.jar";
    private String testBinDir = "./.probe_test_classes";

    public TestClassCompiler() {
    }

    public void compileTestClass(String TestSrcPath, String mainBinDir) {

        //create dir
        try {
            Files.createDirectory(Paths.get(mainBinDir));
        } catch (IOException e) {
            System.out.println();
        }

        String[] args = {"-cp", getJunitStandaloneDir() + "/" + junitStandaloneName + ":" + mainBinDir, "-d", testBinDir, TestSrcPath};
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        System.out.println("javac " + args);
        int rc = javac.run(null, null, null, args);
        if (rc != 0) {
            throw new RuntimeException("failed to compile.");
        }
    }

    public String getJunitStandaloneName() {
        return junitStandaloneName;
    }

    public void setJunitStandaloneName(String junitStandaloneName) {
        this.junitStandaloneName = junitStandaloneName;
    }

    public String getTestBinDir() {
        return testBinDir;
    }

    public void setTestBinDir(String testBinDir) {
        this.testBinDir = testBinDir;
    }

    public String getJunitStandaloneDir() {
        return junitStandaloneDir;
    }

    public void setJunitStandaloneDir(String junitStandaloneDir) {
        this.junitStandaloneDir = junitStandaloneDir;
    }
}