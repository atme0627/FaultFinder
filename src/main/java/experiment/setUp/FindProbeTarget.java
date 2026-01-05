package experiment.setUp;

import experiment.defect4j.Defects4jUtil;
import experiment.util.SuspiciousVariableFinder;
import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.JsonIO;
import jisd.fl.core.entity.MethodElementName;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
        //43はheap space不足でできない
        List<Integer> duplicatedBugs = List.of(2, 18, 25, 43, 48);
        int targetBugId = 21;

        for(int bugId = targetBugId; bugId <= targetBugId; bugId++) {
            File outputFile = expDir.resolve(project + "/" + project.toLowerCase() + "_" + bugId + "b/probeTargets_new.json").toFile();
            Path path = outputFile.toPath();
            Files.createDirectories(path.getParent());
            Files.deleteIfExists(path);
            Files.createFile(path);

            if (duplicatedBugs.contains(bugId)) continue;
            JSONObject bundle = findProbeTarget(project, bugId);
            System.out.println(bundle.toString(4));
            JsonIO.outputJsonToFile(bundle, outputFile);
        }
    }

    public static JSONObject findProbeTarget(String project, int bugId) throws IOException {
        System.out.println("Finding target: [PROJECT] " + project + "  [BUG ID] " + bugId);
        Defects4jUtil.changeTargetVersion(project, bugId);
        Defects4jUtil.compileBuggySrc(project, bugId);
        List<SuspiciousVariable> result = new ArrayList<>();
        List<MethodElementName> failedMethods = Defects4jUtil.getFailedTestMethods("Lang", bugId);
        for (MethodElementName me : failedMethods) {
            SuspiciousVariableFinder finder = new SuspiciousVariableFinder(me);
            result.addAll(finder.findSuspiciousVariableInAssertLine());
        }

        result.forEach(vi -> System.out.println(vi.toString()));
        return convertToJsonAndBundle(result);
    }

    private static JSONObject convertToJsonAndBundle(List<SuspiciousVariable> result){
        JSONArray array = new JSONArray();
        for(SuspiciousVariable vi : result) {
            array.put(vi.toJson());
        }
        JSONObject bundle = new JSONObject();
        bundle.put("suspiciousVariables", array);
        return bundle;
    }

}
