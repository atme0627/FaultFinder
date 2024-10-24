package probe;

import jisd.debug.Debugger;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TestDebuggerFactory {
    private String binDir;
    private String junitStandalone;

    TestDebuggerFactory(){
        this.setBinDir("./.probe_test_classes");
        this.setJunitStandaloneName("junit-platform-console-standalone-1.10.0.jar");
    }

    public Debugger create(String testClassName, String testMethodName, String testJavaFilePath, String mainBinDir, String junitStandaloneDir){
        compileTestClass(testJavaFilePath, mainBinDir, junitStandaloneDir);
        String main = "-jar " + junitStandaloneDir + "/" + getJunitStandaloneName() + " -cp " + getBinDir() + " --select-method=" + testClassName + "#" + testMethodName;
        String option = "-cp " + binDir;
        return new Debugger(main, option);
    }

    public void compileTestClass(String TestSrcPath, String mainBinDir, String junitStandaloneDir){

        //create dir
        try{
            Files.createDirectory(Paths.get(mainBinDir));
        }catch(IOException e){
            System.out.println();
        }

        String[] args = {"-cp", junitStandaloneDir + "/" + getJunitStandaloneName() + ":" + mainBinDir, "-d", getBinDir(), TestSrcPath};
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        System.out.println("javac " + args);
        int rc = javac.run(null, null, null, args);
        if(rc != 0){
            throw new RuntimeException("failed to compile.");
        }

    }

    public String getBinDir() {
        return binDir;
    }

    public String getJunitStandaloneName() {
        return junitStandalone;
    }

    public void setBinDir(String binDir) {
        this.binDir = binDir;
    }

    public void setJunitStandaloneName(String junitStandaloneName) {
        this.junitStandalone = junitStandaloneName;
    }
}
