package experiment.main;

import experiment.coverage.CoverageGenerator;
import experiment.defect4j.Defects4jUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Coverage {
    public static void main(String[] args){
        String project = "Math";
        int number0fBugs = 106;

        for(int bugId = 1; bugId <= number0fBugs; bugId++){
        System.out.println("Coverage measurement: [PROJECT] " + project + "  [BUG ID] " + bugId);
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
    }
}
