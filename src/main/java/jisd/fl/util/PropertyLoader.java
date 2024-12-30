package jisd.fl.util;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class PropertyLoader {
    private static final String[] CONF_FILES = new String[]{
            "fl_properties/fl_config.properties",
            "fl_properties/fl_jacoco.properties",
            "fl_properties/fl_junit.properties"
    };

    private static final String FL_CONF = "fl_properties/fl_config.properties";

    private static final Properties properties;

    private PropertyLoader() throws Exception {
    }

    static {
        properties = new Properties();
        for(String CONF_FILE : CONF_FILES) {
            try {
                properties.load(Files.newBufferedReader(Paths.get(CONF_FILE), StandardCharsets.UTF_8));
            } catch (IOException e) {
                // ファイル読み込みに失敗
                System.out.printf("Failed to load fi_config file. :%s%n", CONF_FILE);
            }
        }
    }

    public static String getProperty(final String key) {
        return properties.getProperty(key);
    }

    public static  String getJunitClassPaths(){
        String junitPlatformLauncher = getProperty("junit-platform-launcher");
        String junitPlatformEngine = getProperty("junit-platform-engine");
        String junitPlatformCommons = getProperty("junit-platform-commons");
        String junitJupiterEngine = getProperty("junit-jupiter-engine");
        String junitJupiterApi = getProperty("junit-jupiter-api");
        String openTest4j = getProperty("opentest4j");
        String junit4 = getProperty("junit4");
        String junitVintageEngine = getProperty("junit-vintage-engine");
        String apiguardian = getProperty("apiguardian");
        String hamcrest = getProperty("hamcrest");

        String cp = junitPlatformLauncher +
                ":" + junitPlatformEngine +
                ":" + junitPlatformCommons +
                ":" + junitJupiterEngine +
                ":" + junitJupiterApi +
                ":" + openTest4j +
                ":" + junit4 +
                ":" + apiguardian +
                ":" + hamcrest +
                ":" + junitVintageEngine;

        return cp;
    }

    public static void setProperty(String key, String value){
        properties.setProperty(key, value);
    }

    public static void store(){
        Properties p = new Properties();
        p.setProperty("targetSrcDir", properties.getProperty("targetSrcDir"));
        p.setProperty("testSrcDir", properties.getProperty("testSrcDir"));
        p.setProperty("targetBinDir", properties.getProperty("targetBinDir"));
        p.setProperty("testBinDir", properties.getProperty("testBinDir"));

        try(FileWriter fw = new FileWriter(FL_CONF)) {
            p.store(fw, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
