package jisd.fl.util;

import java.io.FileWriter;
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
    private static final String FL_CONF = "fl_properties/fl_config.properties";
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

    public static String getProperty(final String key) {
        return properties.getProperty(key);
    }

    public static String getJunitClassPaths() {
        return getProperty("junitDependencyJars");
    }

    public static void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    public static void store() {
        Properties p = new Properties();
        p.setProperty("targetSrcDir", properties.getProperty("targetSrcDir"));
        p.setProperty("testSrcDir", properties.getProperty("testSrcDir"));
        p.setProperty("targetBinDir", properties.getProperty("targetBinDir"));
        p.setProperty("testBinDir", properties.getProperty("testBinDir"));

        try (FileWriter fw = new FileWriter(FL_CONF)) {
            p.store(fw, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setTargetSrcDir(String targetSrcDir) {
        setProperty("targetSrcDir", targetSrcDir);
    }

    public static void setTargetBinDir(String targetBinDir) {
        setProperty("targetBinDir", targetBinDir);
    }

    public static void setTestSrcDir(String testSrcDir) {
        setProperty("testSrcDir", testSrcDir);
    }

    public static void setTestBinDir(String testBinDir) {
        setProperty("testBinDir", testBinDir);
    }

    public static void setDebugBinDir(String debugBinDir) {
        setProperty("debugBinDir", debugBinDir);
    }
    public static String getTargetSrcDir() {
        return getProperty("targetSrcDir");
    }

    public static String getTargetBinDir() {
        return getProperty("targetBinDir");
    }

    public static String getTestSrcDir() {
        return getProperty("testSrcDir");
    }

    public static String getTestBinDir() {
        return getProperty("testBinDir");
    }

    public static String getDebugBinDir() {
        return getProperty("debugBinDir");
    }
}
