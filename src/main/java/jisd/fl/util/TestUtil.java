package jisd.fl.util;

import jisd.debug.DebugResult;
import jisd.debug.Debugger;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

public  class TestUtil {
    public static void compileTestClass(String testClassName) {
        final String compiledWithJunitFilePath = PropertyLoader.getProperty("compiledWithJunitFilePath");
        final String targetBinDir = PropertyLoader.getProperty("targetBinDir");
        final String testSrcDir = PropertyLoader.getProperty("testSrcDir");
        final String testBinDir = PropertyLoader.getProperty("testBinDir");
        final String junitClassPath = PropertyLoader.getJunitClassPaths();

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
        final String jacocoAgentPath = PropertyLoader.getProperty("jacocoAgentPath");
        final String jacocoExecFilePath = PropertyLoader.getProperty("jacocoExecFilePath");
        final String targetBinDir = PropertyLoader.getProperty("targetBinDir");
        final String testBinDir = PropertyLoader.getProperty("testBinDir");
        final String junitClassPath = PropertyLoader.getJunitClassPaths();

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

        //debug
//        String line = null;
//        System.out.println("STDOUT---------------");
//        try ( var buf = new BufferedReader( new InputStreamReader( proc.getInputStream() ) ) ) {
//            while( ( line = buf.readLine() ) != null ) System.out.println( line );
//        }
//        System.out.println("STDERR---------------");
//        try ( var buf = new BufferedReader( new InputStreamReader( proc.getErrorStream() ) ) ) {
//            while( ( line = buf.readLine() ) != null ) System.out.println( line );
//        }

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
        final String targetBinDir = PropertyLoader.getProperty("targetBinDir");
        final String testBinDir = PropertyLoader.getProperty("testBinDir");
        final String junitClassPath = PropertyLoader.getJunitClassPaths();
        String targetSrcDir = PropertyLoader.getProperty("targetSrcDir");
        String testSrcDir = PropertyLoader.getProperty("testSrcDir");

        Debugger dbg = new Debugger("jisd.fl.util.TestLauncher " + testMethodName,
                "-cp " + "./build/classes/java/main" + ":" + testBinDir + ":" + targetBinDir + ":" + junitClassPath);


        dbg.setSrcDir(targetSrcDir, testSrcDir);
        DebugResult.setDefaultMaxRecordNoOfValue(1000);
        return dbg;
    }
}