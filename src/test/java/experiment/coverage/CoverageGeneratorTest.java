package experiment.coverage;

import experiment.defect4j.Defects4jUtil;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestLauncher;
import jisd.fl.util.TestUtil;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class CoverageGeneratorTest {
    @Test
    void genTest(){
        String project = "Math";
        int bugId = 1;

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
    void debug() throws IOException, InterruptedException {
        String project = "Math";
        int bugId = 1;
        Defects4jUtil.changeTargetVersion(project, bugId);
        String targetBinDir = PropertyLoader.getProperty("targetBinDir");
        String testBinDir = PropertyLoader.getProperty("testBinDir");

        String junitClassPath = PropertyLoader.getJunitClassPaths();
        String testMethodName = "org.apache.commons.math3.fraction.BigFractionTest#testDoubleValue";

        Defects4jUtil.CompileBuggySrc(project, bugId);

        String cmd = "java -cp " + "./build/classes/java/main" + ":./.probe_test_classes" + ":" + targetBinDir + ":" + testBinDir + ":" + junitClassPath + " jisd.fl.util.TestLauncher " + testMethodName;
        Process proc;
        try {
            proc = Runtime.getRuntime().exec(cmd);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String line = null;

        try ( var buf = new BufferedReader( new InputStreamReader( proc.getInputStream() ) ) ) {
            while( ( line = buf.readLine() ) != null ) System.out.println( line );
        }

        proc.waitFor();
        System.out.println("exit: " + proc.exitValue());

    }

    @Test
    void debug2(){
        String project = "Math";
        int bugId = 87;
        String testMethod = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testMath272";
        Defects4jUtil.changeTargetVersion(project, bugId);
        Defects4jUtil.CompileBuggySrc(project, bugId);
        TestLauncher.main(new String[]{testMethod});

    }
}