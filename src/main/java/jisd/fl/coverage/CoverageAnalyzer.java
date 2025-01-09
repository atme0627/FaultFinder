package jisd.fl.coverage;

import jisd.fl.util.*;
import org.jacoco.core.data.ExecutionDataStore;

import java.io.*;
import java.util.Set;

//テストケースを実行して、jacoco.execファイルを生成するクラス
public class CoverageAnalyzer {
    String jacocoExecFilePath = PropertyLoader.getProperty("jacocoExecFilePath");
    final String targetSrcDir = PropertyLoader.getProperty("targetSrcDir");;
    String outputDir;
    Set<String> targetClassNames;
    Set<String> failedTests;

    public CoverageAnalyzer(){
        this("./.coverage_data");
    }

    public CoverageAnalyzer(String outputDir){
        this(outputDir, null);
    }

    public CoverageAnalyzer(String outputDir, Set<String> failedTests) {
        this.outputDir = outputDir;
        this.failedTests = failedTests;
        targetClassNames = StaticAnalyzer.getClassNames(targetSrcDir);
    }

    public CoverageCollection analyzeAll(String testClassName) throws IOException, InterruptedException {
        return analyzeAll(testClassName, false);
    }

    public CoverageCollection analyzeAll(String testClassName, boolean cache) throws IOException, InterruptedException{
        String serializedFileName = testClassName;
        //デシリアライズ処理
        if(cache && isCovDataExist(serializedFileName)){
            return deserialize(serializedFileName);
        }

        Set<String> testMethodNames = StaticAnalyzer.getMethodNames(testClassName, true,true, true, false);

        //テストクラスをコンパイル
        TestUtil.compileTestClass(testClassName);
        MyCoverageVisiter cv = new MyCoverageVisiter(testClassName, targetClassNames);

        for(String testMethodName : testMethodNames){
            //execファイルの生成
            //テストケースをjacocoAgentつきで実行
            String jacocoExecName = testMethodName + ".jacocoexec";
            boolean isTestPassed = TestUtil.execTestCaseWithJacocoAgent(testMethodName, jacocoExecName);

            //テストの成否が想定と一致しているか確認
            if(failedTests != null){
                if((isTestPassed && failedTests.contains(testMethodName))
                || (!isTestPassed && !failedTests.contains(testMethodName))){
                    throw new RuntimeException("Execution result is wrong. [testcase] " + testMethodName);
                }
            }

            cv.setTestsPassed(isTestPassed);
            ExecutionDataStore execData = JacocoUtil.execFileLoader(jacocoExecName);
            JacocoUtil.analyzeWithJacoco(execData, cv);
        }
        FileUtil.initDirectory(jacocoExecFilePath);
        //シリアライズ処理
        serialize(cv.getCoverages(), serializedFileName);
        return cv.getCoverages();
    }

//    public CoverageCollection analyzeAllWithAPI(String testClassName) throws Exception {
//        //デシリアライズ処理
//        if(isCovDataExist(testClassName)){
//            return deserialize(testClassName);
//        }
//
//        Set<String> testMethodNames = StaticAnalyzer.getMethodNames(testClassName, true, true, true, false);
//
//        //テストクラスをコンパイル
//        TestUtil.compileTestClass(testClassName);
//        MyCoverageVisiter cv = new MyCoverageVisiter(testClassName, targetClassNames);
//
//        JacocoUtil jacocoUtil= new JacocoUtil();
//        for(String testMethodName : testMethodNames){
//            Pair<Boolean, ExecutionDataStore> execWithAPI = jacocoUtil.execTestCaseWithJacocoAPI(testMethodName);
//            boolean isTestPassed = execWithAPI.getLeft();
//            ExecutionDataStore execData = execWithAPI.getRight();
//
//            cv.setTestsPassed(isTestPassed);
//            JacocoUtil.analyzeWithJacoco(execData, cv);
//        }
//
//        //TestLauncherをリロード
//        //ClassLoader.getSystemClassLoader().loadClass(TestLauncher.class.getName());
//        //シリアライズ処理
//        serialize(cv.getCoverages(), "");
//        return cv.getCoverages();
//    }
//
//    public CoverageCollection analyze(String testClassName, String testMethodName) throws IOException, InterruptedException{
//        //execファイルの生成
//        //テストケースをjacocoAgentつきで実行
//        MyCoverageVisiter cv = new MyCoverageVisiter(testClassName, targetClassNames);
//        String jacocoExecName = testMethodName + ".jacocoexec";
//        boolean isTestPassed = TestUtil.execTestCaseWithJacocoAgent(testMethodName, jacocoExecName);
//        cv.setTestsPassed(isTestPassed);
//        ExecutionDataStore execData = JacocoUtil.execFileLoader(jacocoExecName);
//        JacocoUtil.analyzeWithJacoco(execData, cv);
//        return cv.getCoverages();
//    }

    private boolean isCovDataExist(String coverageCollectionName){
        String covFileName = outputDir + "/" + coverageCollectionName + ".cov";
        File data = new File(covFileName);
        return data.exists();
    }

    private void serialize(CoverageCollection cc, String serializedFileName){
        String covFileName = outputDir + "/" + serializedFileName + ".cov";
        FileUtil.createDirectory(outputDir);
        File data = new File(covFileName);

        try {
            data.createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(covFileName);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(cc);
            objectOutputStream.flush();
            objectOutputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private CoverageCollection deserialize(String coverageCollectionName){
        String covFileName = outputDir + "/" + coverageCollectionName + ".cov";

        try {
            FileInputStream fileInputStream = new FileInputStream(covFileName);
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            CoverageCollection cc = (CoverageCollection) objectInputStream.readObject();
            objectInputStream.close();
            return cc;
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}

