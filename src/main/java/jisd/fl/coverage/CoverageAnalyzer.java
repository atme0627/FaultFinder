package jisd.fl.coverage;

import jisd.fl.util.*;
import org.apache.commons.lang3.tuple.Pair;
import org.jacoco.core.data.ExecutionDataStore;

import java.io.*;
import java.util.Set;

//テストケースを実行して、jacoco.execファイルを生成するクラス
public class CoverageAnalyzer {
    final String jacocoExecFilePath = PropertyLoader.getProperty("jacocoExecFilePath");
    final String testSrcDir = PropertyLoader.getProperty("d4jTestSrcDir");
    final String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");;
    Set<String> targetClassNames;

    public CoverageAnalyzer() throws IOException {
        FileUtil.initDirectory(jacocoExecFilePath);
        targetClassNames = StaticAnalyzer.getClassNames(targetSrcDir);
    }

    public CoverageCollection analyzeAll(String testClassName) throws IOException, InterruptedException{
        //デシリアライズ処理
        if(isCovDataExist(testClassName)){
            return deserialize(testClassName);
        }

        Set<String> testMethodNames = StaticAnalyzer.getMethodNames(testClassName, true);

        //テストクラスをコンパイル
        TestUtil.compileTestClass(testClassName);
        MyCoverageVisiter cv = new MyCoverageVisiter(testClassName, targetClassNames);

        for(String testMethodName : testMethodNames){
            //execファイルの生成
            //テストケースをjacocoAgentつきで実行
            String jacocoExecName = testMethodName + ".jacocoexec";
            boolean isTestPassed = TestUtil.execTestCaseWithJacocoAgent(testMethodName, jacocoExecName);
            cv.setTestsPassed(isTestPassed);
            ExecutionDataStore execData = JacocoUtil.execFileLoader(jacocoExecName);
            JacocoUtil.analyzeWithJacoco(execData, cv);
        }

        //シリアライズ処理
        serialize(cv.getCoverages());
        return cv.getCoverages();
    }

    public CoverageCollection analyzeAllWithAPI(String testClassName) throws Exception {
        //デシリアライズ処理
        if(isCovDataExist(testClassName)){
            return deserialize(testClassName);
        }

        Set<String> testMethodNames = StaticAnalyzer.getMethodNames(testClassName, true);

        //テストクラスをコンパイル
        TestUtil.compileTestClass(testClassName);
        MyCoverageVisiter cv = new MyCoverageVisiter(testClassName, targetClassNames);

        JacocoUtil jacocoUtil= new JacocoUtil();
        for(String testMethodName : testMethodNames){
            Pair<Boolean, ExecutionDataStore> execWithAPI = jacocoUtil.execTestCaseWithJacocoAPI(testMethodName);
            boolean isTestPassed = execWithAPI.getLeft();
            ExecutionDataStore execData = execWithAPI.getRight();

            cv.setTestsPassed(isTestPassed);
            JacocoUtil.analyzeWithJacoco(execData, cv);
        }

        //TestLauncherをリロード
        ClassLoader.getSystemClassLoader().loadClass(TestLauncher.class.getName());
        //シリアライズ処理
        serialize(cv.getCoverages());
        return cv.getCoverages();
    }

    public CoverageCollection analyze(String testClassName, String testMethodName) throws IOException, InterruptedException{
        //execファイルの生成
        //テストケースをjacocoAgentつきで実行
        MyCoverageVisiter cv = new MyCoverageVisiter(testClassName, targetClassNames);
        String jacocoExecName = testMethodName + ".jacocoexec";
        boolean isTestPassed = TestUtil.execTestCaseWithJacocoAgent(testMethodName, jacocoExecName);
        cv.setTestsPassed(isTestPassed);
        ExecutionDataStore execData = JacocoUtil.execFileLoader(jacocoExecName);
        JacocoUtil.analyzeWithJacoco(execData, cv);
        return cv.getCoverages();
    }

    private boolean isCovDataExist(String coverageCollectionName){
        String dirPath = "./.coverage_data";
        String covFileName = dirPath + "/" + coverageCollectionName + ".cov";
        File data = new File(covFileName);
        return data.exists();
    }

    private void serialize(CoverageCollection cc){
        String dirPath = "./.coverage_data";
        String covFileName = dirPath + "/" + cc.coverageCollectionName + ".cov";
        FileUtil.createDirectory(dirPath);
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
        String dirPath = "./.coverage_data";
        String covFileName = dirPath + "/" + coverageCollectionName + ".cov";

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

