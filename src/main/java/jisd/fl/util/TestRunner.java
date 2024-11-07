package jisd.fl.util;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public  class TestRunner {
    private static final String junitConsoleLauncherPath = PropertyLoader.getProperty("junitConsoleLauncherPath");
    private static final String compiledWithJunitFilePath = PropertyLoader.getProperty("compiledWithJunitFilePath");
    private static final String jacocoAgentPath = PropertyLoader.getProperty("jacocoAgentPath");
    private static final String jacocoExecFilePath = PropertyLoader.getProperty("jacocoExecFilePath");
    private static final String targetBinDir = PropertyLoader.getProperty("targetBinDir");
    private static final String testSrcDir = PropertyLoader.getProperty("testSrcDir");

    public static void compileTestClass(String testClassName) {

        DirectoryUtil.initDirectory(compiledWithJunitFilePath);

        String[] args = {"-cp", junitConsoleLauncherPath + ":" + targetBinDir,  testSrcDir + "/" + testClassName.replace(".", "/") + ".java", "-d", compiledWithJunitFilePath};

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        System.out.println("javac " + Arrays.toString(args));
        int rc = javac.run(null, null, null, args);
        if (rc != 0) {
            throw new RuntimeException("failed to compile.");
        }
    }

    //true: 成功    false: 失敗
    public static Boolean execTestCase(String testMethodName) throws IOException, InterruptedException {
        String cmd = "java -jar " + junitConsoleLauncherPath + " -cp " + targetBinDir + ":" +
                compiledWithJunitFilePath + " --select-method " + testMethodName;

        Process proc = Runtime.getRuntime().exec(cmd);
        proc.waitFor();
        return proc.exitValue() == 0;
    }

    //junit console launcherにjacoco agentをつけて起動
    //methodNameは次のように指定: org.example.order.OrderTests#test1
    //先にTestClassCompilerでテストクラスをjunitConsoleLauncherとともにコンパイルする必要がある
    //TODO: execファイルの生成に時間がかかりすぎるため、並列化の必要あり
    public static boolean execTestCaseWithJacocoAgent(String testClassName , String execFileName) throws IOException, InterruptedException {
        String generatedFilePath = jacocoExecFilePath + "/" + execFileName;
        String junitTestSelectOption =" --select-method " + testClassName;

        String cmd = "java -javaagent:" + jacocoAgentPath + "=destfile=" + generatedFilePath +
                " -jar " + junitConsoleLauncherPath + " -cp " + targetBinDir + ":" +
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
}