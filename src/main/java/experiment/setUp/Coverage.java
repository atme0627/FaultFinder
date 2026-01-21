package experiment.setUp;

import experiment.defect4j.Defects4jUtil;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.infra.jacoco.ProjectSbflCoverage;
import jisd.fl.sbfl.coverage.CoverageAnalyzer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.util.JsonIO;
import jisd.fl.core.entity.element.MethodElementName;

public class Coverage {
    static Dotenv dotenv = Dotenv.load();
    static Path expDir = Paths.get(dotenv.get("EXP_20250726_DIR"));

    public static void main(String[] args) throws InterruptedException, IOException {
        String project = "Lang";
        int numberOfBugs = 61;
        List<Integer> duplicatedBugs = List.of(2, 18, 25, 48);

        boolean CACHE = false;

        for(int bugId = 34; bugId <= numberOfBugs; bugId++){
            if(duplicatedBugs.contains(bugId)) continue;
            File outputFile = expDir.resolve(project + "/" + project.toLowerCase() + "_" + bugId + "b/coverage.json").toFile();

            System.out.println("Coverage measurement: [PROJECT] " + project + "  [BUG ID] " + bugId);

            if(CACHE) {
                if (outputFile.exists() && outputFile.length() != 0) {
                    System.out.println("Already generated.");
                    continue;
                }
            }
            else {
                Path path = outputFile.toPath();
                Files.deleteIfExists(path);
                Files.createFile(path);
            }

            Defects4jUtil.changeTargetVersion(project, bugId);
            Defects4jUtil.compileBuggySrc(project, bugId);
            List<MethodElementName> testMethods = Defects4jUtil.getFailedTestMethods(project, bugId);
            Set<MethodElementName> failedTests = new HashSet<>(testMethods);
            CoverageAnalyzer ca = new CoverageAnalyzer(failedTests);
            Set<ClassElementName> executed = new HashSet<>();
            for(MethodElementName testMethodName : testMethods) {
                ClassElementName testClassName = testMethodName.classElementName;

                if(executed.contains(testClassName)) continue;
                executed.add(testClassName);

                ca.analyze(testClassName);
                //TODO: テストの実行結果が正しいかをチェックするバリデーションを行う。

            }
//            JsonIO.export(ca.result(), outputFile);
//            ca.result().free();
        }

        Thread.sleep(100);
    }
}
