package jisd.fl.util;

import jisd.debug.Debugger;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public  class TestUtil {
    private static final String junitConsoleLauncherPath = PropertyLoader.getProperty("junitConsoleLauncherPath");
    private static final String compiledWithJunitFilePath = PropertyLoader.getProperty("compiledWithJunitFilePath");
    private static final String jacocoAgentPath = PropertyLoader.getProperty("jacocoAgentPath");
    private static final String jacocoExecFilePath = PropertyLoader.getProperty("jacocoExecFilePath");
    private static final String targetBinDir = PropertyLoader.getProperty("d4jTargetBinDir");
    private static final String testSrcDir = PropertyLoader.getProperty("d4jTestSrcDir");
    private static final String testBinDir = PropertyLoader.getProperty("d4jTestBinDir");

    private static final String junitClassPath = PropertyLoader.getProperty("junit4");

    public static void compileTestClass(String testClassName) {

        FileUtil.initDirectory(compiledWithJunitFilePath);

        String[] args = {"-cp", junitClassPath + ":" + targetBinDir + ":" + testBinDir,  testSrcDir + "/" + testClassName.replace(".", "/") + ".java", "-d", compiledWithJunitFilePath};

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        System.out.println("javac " + Arrays.toString(args));
        int rc = javac.run(null, null, null, args);
        if (rc != 0) {
            throw new RuntimeException("failed to compile.");
        }
    }

    //junit console launcherにjacoco agentをつけて起動
    //methodNameは次のように指定: org.example.order.OrderTests#test1
    //先にTestClassCompilerでテストクラスをjunitConsoleLauncherとともにコンパイルする必要がある
    //TODO: execファイルの生成に時間がかかりすぎるため、並列化の必要あり
    public static boolean execTestCaseWithJacocoAgent(String testClassName , String execFileName) throws IOException, InterruptedException {
        String generatedFilePath = jacocoExecFilePath + "/" + execFileName;
        String junitTestSelectOption =" --select-method " + testClassName;

        String cmd = "java -javaagent:" + jacocoAgentPath + "=destfile=" + generatedFilePath +
                " -jar " + junitConsoleLauncherPath + " -cp " + targetBinDir + ":" + testBinDir + ":" +
                compiledWithJunitFilePath + junitTestSelectOption;

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

    public static Debugger testDebuggerFactory(String testClassName, String testMethodName) {
        String testBinDir = PropertyLoader.getProperty("d4jTestBinDir");
        String targetBinDir = PropertyLoader.getProperty("d4jTargetBinDir");
        String junitClassPath = PropertyLoader.getJunitClassPaths();

        Debugger dbg = new Debugger("jisd.fl.util.TestLauncher " + testClassName + " " + testMethodName,
                "-cp " + "./build/classes/java/main" + ":" + testBinDir + ":" + targetBinDir + ":" + junitClassPath);


        dbg.setMain(testClassName);
        return dbg;

    }

//    public static boolean execTestCaseWithJacocoAPI(String coverageCollectionName, String execFileName){
//        String generatedFilePath = jacocoExecFilePath + "/" + execFileName;
//
//
//    }
}