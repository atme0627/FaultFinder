package jisd.fl.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.io.InputStream;

public class PropertyLoader {
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
