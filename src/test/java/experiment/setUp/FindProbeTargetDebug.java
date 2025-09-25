package experiment.setUp;

import experiment.defect4j.Defects4jUtil;
import experiment.util.SuspiciousVariableFinder;
import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.JsonIO;
import jisd.fl.util.analyze.MethodElementName;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FindProbeTargetDebug {
    public static void main(String[] args) throws IOException {
        String project = "Lang";
        List<Integer> duplicatedBugs = List.of(2, 18, 25, 48);

        int targetBugId = 1;
        JSONObject bundle = FindProbeTarget.findProbeTarget(project, targetBugId);
        System.out.println(bundle.toString(4));
    }
}

