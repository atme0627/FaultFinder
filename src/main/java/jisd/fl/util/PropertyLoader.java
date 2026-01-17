package jisd.fl.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.io.InputStream;

public class PropertyLoader {
    private static final String[] CONF_FILES = new String[]{
            "fl_properties/fl_config.properties",
            "fl_properties/fl_jacoco.properties",
            "fl_properties/fl_junit.properties"
    };
    private static final Properties properties;

    static {
        properties = new Properties();
        for (String CONF_FILE : CONF_FILES) {
            try (InputStream input = PropertyLoader.class.getClassLoader().getResourceAsStream(CONF_FILE)) {
                properties.load(input);
            } catch (IOException e) {
                // ファイル読み込みに失敗
                System.out.printf("Failed to load fi_config file. :%s%n", CONF_FILE);
            } catch (NullPointerException e) {
                try {
                    properties.load(Files.newBufferedReader(Paths.get(CONF_FILE), StandardCharsets.UTF_8));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    public static String getJunitClassPaths() {
        return "locallib/junit-dependency/*";
    }

    public static String getTargetSrcDir() {
        return NewPropertyLoader.getTargetSrcDir().toString();
    }

    public static String getTargetBinDir() {
        return NewPropertyLoader.getTargetBinDir().toString();
    }

    public static String getTestSrcDir() {
        return NewPropertyLoader.getTestSrcDir().toString();
    }

    public static String getTestBinDir() {
        return NewPropertyLoader.getTestBinDir().toString();
    }

    public static String getDebugBinDir() {
        return "/Users/ezaki/IdeaProjects/MyFaultFinder/classesForDebug/";
    }

    public static void setProjectConfig(NewPropertyLoader.ProjectConfig cfg) {
        NewPropertyLoader.setProjectConfig(cfg);
    }
}
