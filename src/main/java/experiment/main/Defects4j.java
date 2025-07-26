package experiment.main;

import experiment.defect4j.Defects4jUtil;

import java.io.File;

public class Defects4j {
    public static void main(String[] arg){
        String project = "Lang";
        Defects4jUtil.checkoutBuggySrc(project, 2, new File("/Users/ezaki/Desktop/research/d4jProjects/Lang"));
    }
}
