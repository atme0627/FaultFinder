package jisd.fl.coverage;

import jisd.fl.util.*;
import org.jacoco.core.data.ExecutionDataStore;

import java.io.IOException;
import java.util.Set;

//テストケースを実行して、jacoco.execファイルを生成するクラス
public class CoverageAnalyzer {
    final String jacocoExecFilePath = PropertyLoader.getProperty("jacocoExecFilePath");
    final String testSrcDir = PropertyLoader.getProperty("d4jTestSrcDir");
    final String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");;
    Set<String> targetClassNames;

    public CoverageAnalyzer() throws IOException {
        DirectoryUtil.initDirectory(jacocoExecFilePath);
        targetClassNames = StaticAnalyzer.getClassNames(targetSrcDir);
    }

    public CoverageCollection analyzeAll(String testClassName) throws IOException, InterruptedException{

        Set<String> testMethodNames = StaticAnalyzer.getMethodNames(testSrcDir, testClassName);

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
}

