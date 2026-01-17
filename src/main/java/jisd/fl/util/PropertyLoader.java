package jisd.fl.util;

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
}
