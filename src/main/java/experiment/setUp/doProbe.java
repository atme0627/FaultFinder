package experiment.setUp;

import com.fasterxml.jackson.core.type.TypeReference;
import experiment.defect4j.Defects4jUtil;
import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.probe.Probe;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.JsonIO;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class doProbe {
    static Dotenv dotenv = Dotenv.load();
    static Path expDir = Paths.get(dotenv.get("EXP_20250726_DIR"));

    public static void main(String[] args) throws IOException {
        String project = "Lang";
        int numberOfBugs = 61;
        List<Integer> duplicatedBugs = List.of(2, 18, 25, 48);
        boolean CACHE = true;

        for(int bugId = 1; bugId <= numberOfBugs; bugId++) {
            File inputFile = expDir.resolve(project + "/" + project.toLowerCase() + "_" + bugId + "b/probeTargets.json").toFile();
            if (duplicatedBugs.contains(bugId)) continue;
            Defects4jUtil.changeTargetVersion(project, bugId);
            Defects4jUtil.compileBuggySrc(project, bugId);

            List<?> probeTargets = JsonIO.importFromJson(inputFile, new TypeReference<List<SuspiciousVariable>>() {});
            if(probeTargets.isEmpty()) continue;

            System.out.println("Finding target: [PROJECT] " + project + "  [BUG ID] " + bugId);

            for(int i = 0; i < probeTargets.size(); i++) {
                SuspiciousVariable target = (SuspiciousVariable) probeTargets.get(i);
                System.out.println("failedTest " + target.getFailedTest());

                File outputFile = expDir.resolve(project + "/" + project.toLowerCase() + "_" + bugId + "b/probe/" +
                        target.getFailedTest() + "_" + target.getSimpleVariableName()).toFile();

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

                Probe prb = new Probe(target);
                SuspiciousExpression result = prb.run(2000);
                JsonIO.export(result, outputFile);
            }
        }

    }
}
