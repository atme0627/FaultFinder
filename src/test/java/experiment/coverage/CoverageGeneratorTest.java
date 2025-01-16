package experiment.coverage;

import com.fasterxml.jackson.core.JsonProcessingException;
import experiment.defect4j.Defects4jUtil;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.StaticAnalyzer;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.NoSuchFileException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class CoverageGeneratorTest {
    @Test
    void genTest(){
        String project = "Math";
        int bugId = 43;

        Defects4jUtil.changeTargetVersion(project, bugId);
        Defects4jUtil.CompileBuggySrc(project, bugId);
        List<String> testMethods = Defects4jUtil.getFailedTestMethods(project, bugId);

        Set<String> executed = new HashSet<>();
        for(String testMethodName : testMethods) {
            String testClassName = testMethodName.split("#")[0];

            if(executed.contains(testClassName)) continue;
            executed.add(testClassName);

            CoverageGenerator cg = new CoverageGenerator(testClassName, project, bugId);
            cg.generate();
        }
    }

    @Test
    void getTestNums() throws NoSuchFileException {
        String project = "Math";
        int bugId = 5;

        Defects4jUtil.changeTargetVersion(project, bugId);
        List<String> failed = Defects4jUtil.getFailedTestMethods(project, bugId);

        Set<String> testClass = new HashSet<>();

        System.out.println("[FAILED]");
        for(String m : failed){
            testClass.add(m.split("#")[0]);
            System.out.println(m);
        }

        System.out.println();
        System.out.println("[TEST NUMS]");
        for(String t : testClass){
            System.out.println(t);
            Set<String> testMethods = StaticAnalyzer.getMethodNames(t, true, true, false, true);
            System.out.println(testMethods.size());
        }

    }

    @Test
    void loadTest() {
        String project = "Math";
        int bugId = 43;
        CoverageCollection cov = CoverageGenerator.loadAll(project, bugId);
    }
}