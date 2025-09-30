package experiment.setUp;

import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

public class FindProbeTargetDebug {
    public static void main(String[] args) throws IOException {
        String project = "Lang";
        List<Integer> duplicatedBugs = List.of(2, 18, 25, 48);

        int targetBugId = 9;
        JSONObject bundle = FindProbeTarget.findProbeTarget(project, targetBugId);
        System.out.println(bundle.toString(4));
    }
}

