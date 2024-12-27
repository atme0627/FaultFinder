package jisd.fl.util;

import jisd.debug.Debugger;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;

public  class TestUtil {
    private static final String junitConsoleLauncherPath = PropertyLoader.getProperty("junitConsoleLauncherPath");
    private static final String compiledWithJunitFilePath = PropertyLoader.getProperty("compiledWithJunitFilePath");
    private static final String jacocoAgentPath = PropertyLoader.getProperty("jacocoAgentPath");
    private static final String jacocoExecFilePath = PropertyLoader.getProperty("jacocoExecFilePath");
    private static final String targetBinDir = PropertyLoader.getProperty("targetBinDir");
    private static final String testSrcDir = PropertyLoader.getProperty("testSrcDir");
    private static final String testBinDir = PropertyLoader.getProperty("testBinDir");

    private static final String junitClassPath = PropertyLoader.getJunitClassPaths();

    public static void compileTestClass(String testClassName) {

        FileUtil.initDirectory(compiledWithJunitFilePath);

        String[] args = {"-cp", junitClassPath + ":" + targetBinDir + ":" + testBinDir,  testSrcDir + "/" + testClassName.replace(".", "/") + ".java", "-d", compiledWithJunitFilePath};

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        //System.out.println("javac " + Arrays.toString(args));
        int rc = javac.run(null, null, null, args);
        if (rc != 0) {
            throw new RuntimeException("failed to compile.");
        }
    }

    //TestLauncherにjacoco agentをつけて起動
    //methodNameは次のように指定: org.example.order.OrderTests#test1(int a)
    //先にTestClassCompilerでテストクラスをjunitConsoleLauncherとともにコンパイルする必要がある
    //TODO: execファイルの生成に時間がかかりすぎるため、並列化の必要あり
    public static boolean execTestCaseWithJacocoAgent(String testMethodNameWithSignature, String execFileName) throws IOException, InterruptedException {
        String testMethodName = testMethodNameWithSignature.split("\\(")[0];
        String generatedFilePath = jacocoExecFilePath + "/" + execFileName;
        String junitTestSelectOption =" --select-method " + testMethodName;

//        String cmd = "java -javaagent:" + jacocoAgentPath + "=destfile=" + generatedFilePath +
//                " -jar " + junitConsoleLauncherPath + " -cp " + targetBinDir + ":" + testBinDir + ":" +
//                compiledWithJunitFilePath + junitTestSelectOption;

        String cmd = "java -javaagent:" + jacocoAgentPath + "=destfile=" + generatedFilePath +
                " -cp " + "./build/classes/java/main" + ":./.probe_test_classes" + ":" + targetBinDir + ":" + testBinDir + ":" + junitClassPath + " jisd.fl.util.TestLauncher " + testMethodName;

        //Junit Console Launcherの終了ステータスは、
        // 1: コンテナやテストが失敗
        // 2: テストが見つからないかつ--fail-if-no-testsが指定されている
        // 0: それ以外
        Process proc = Runtime.getRuntime().exec(cmd);
        proc.waitFor();

        //execファイルが生成されるまで待機
        while(true){
            File f = new File(generatedFilePath);
            if(f.exists()){
                break;
            }
        }
        //ファイルの生成が行われたことを出力
        System.out.println("Success to generate " + generatedFilePath + ".");
        System.out.println("testResult " + (proc.exitValue() == 0 ? "o" : "x"));
        return proc.exitValue() == 0;
    }

    public static Debugger testDebuggerFactory(String testMethodName) {

        Debugger dbg = new Debugger("jisd.fl.util.TestLauncher " + testMethodName,
                "-cp " + "./build/classes/java/main" + ":" + testBinDir + ":" + targetBinDir + ":" + junitClassPath);

        return dbg;

    }
}