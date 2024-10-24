package jisd.fl.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class PropertyLoader {
    private static final String CONF_FILE = "fl_config.properties";
    private static final Properties properties;

    private PropertyLoader() throws Exception {
    }

    static {
        properties = new Properties();
        try {
            properties.load(Files.newBufferedReader(Paths.get(CONF_FILE), StandardCharsets.UTF_8));
        } catch (IOException e) {
            // ファイル読み込みに失敗
            System.out.printf("Failed to load fi_config file. :%s%n", CONF_FILE);
        }
    }

    public static String getProperty(final String key) {
        return properties.getProperty(key);
    }
}
