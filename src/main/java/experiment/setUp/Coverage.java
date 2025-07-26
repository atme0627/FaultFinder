package experiment.setUp;

import experiment.defect4j.Defects4jUtil;
import jisd.fl.sbfl.coverage.CoverageAnalyzer;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.util.JsonIO;
import jisd.fl.util.analyze.MethodElementName;

public class Coverage {
    static Dotenv dotenv = Dotenv.load();
    static Path d4jDir = Paths.get(dotenv.get("D4J_DIR"));
    static Path expDir = Paths.get(dotenv.get("EXP_20250726_DIR"));

    public static void main(String[] args) throws InterruptedException {
        String project = "Lang";
        int number0fBugs = 61;
        List<Integer> duplicatedBugs = List.of(2, 18, 25, 48);

        for(int bugId = 1; bugId <= 1; bugId++){
            if(duplicatedBugs.contains(bugId)) continue;
            File outputFile = expDir.resolve(project + "/" + project.toLowerCase() + "_" + bugId + "b/coverage.json").toFile();

            System.out.println("Coverage measurement: [PROJECT] " + project + "  [BUG ID] " + bugId);

            if(outputFile.exists() && outputFile.length() != 0) {
                System.out.println("Already generated.");
                continue;
            }

            Defects4jUtil.changeTargetVersion(project, bugId);
            Defects4jUtil.CompileBuggySrc(project, bugId);
            List<MethodElementName> testMethods = Defects4jUtil.getFailedTestMethods(project, bugId);

            CoverageAnalyzer ca = new CoverageAnalyzer();
            Set<String> executed = new HashSet<>();
            for(MethodElementName testMethodName : testMethods) {
                String testClassName = testMethodName.getFullyQualifiedClassName();

                if(executed.contains(testClassName)) continue;
                executed.add(testClassName);

                ca.analyze(testClassName);
            }
            JsonIO.exportCoverage(ca.result(), outputFile);
        }

        Thread.sleep(100);
    }
}
