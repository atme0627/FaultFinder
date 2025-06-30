package experiment.main;

import experiment.defect4j.Defects4jUtil;

public class Defects4j {
    public static void main(String[] arg){
        String project = "Math";
        int number0fBugs = 25;
        Defects4jUtil.checkoutBuggySrc(project, number0fBugs);
    }
}
