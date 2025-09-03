package experiment.setUp;

import experiment.defect4j.Defects4jUtil;
import experiment.util.SuspiciousVariableFinder;
import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.JsonIO;
import jisd.fl.util.analyze.MethodElementName;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FindProbeTarget {
    static Dotenv dotenv = Dotenv.load();
    static Path expDir = Paths.get(dotenv.get("EXP_20250904_DIR"));

    public static void main(String[] args) throws IOException {
        String project = "Lang";
        int numberOfBugs = 61;
        List<Integer> duplicatedBugs = List.of(2, 18, 25, 48);

        for(int bugId = 1; bugId <= numberOfBugs; bugId++) {
            if (duplicatedBugs.contains(bugId)) continue;
            File outputFile = expDir.resolve(project + "/" + project.toLowerCase() + "_" + bugId + "b/probeTargets.json").toFile();
            Path path = outputFile.toPath();
            Files.createDirectories(path.getParent());
            Files.deleteIfExists(path);
            Files.createFile(path);

            System.out.println("Finding target: [PROJECT] " + project + "  [BUG ID] " + bugId);

            Defects4jUtil.changeTargetVersion(project, bugId);
            Defects4jUtil.compileBuggySrc(project, bugId);
            List<SuspiciousVariable> result = new ArrayList<>();
            List<MethodElementName> failedMethods = Defects4jUtil.getFailedTestMethods("Lang", bugId);
            for (MethodElementName me : failedMethods) {
                SuspiciousVariableFinder finder = new SuspiciousVariableFinder(me);
                result.addAll(finder.find());
            }

            result.forEach(vi -> System.out.println(vi.toString()));
            JsonIO.export(result, outputFile);

        }

    }
}
