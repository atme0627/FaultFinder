package experiment.setUp;

import experiment.defect4j.Defects4jUtil;
import io.github.cdimascio.dotenv.Dotenv;


import java.nio.file.Path;
import java.nio.file.Paths;

public class Defects4j {
    static Dotenv dotenv = Dotenv.load();
    static Path d4jDir = Paths.get(dotenv.get("D4J_DIR"));
    public static void main(String[] arg){
        String project = "Lang";
        Defects4jUtil.checkoutBuggySrc(project, 8, d4jDir.toFile());
    }
}
