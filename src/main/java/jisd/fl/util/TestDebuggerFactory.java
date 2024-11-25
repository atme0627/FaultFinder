package jisd.fl.util;

import jisd.debug.Debugger;
import jisd.fl.util.TestLauncher;

public class TestDebuggerFactory {
    public TestDebuggerFactory() {
    }
    //TODO: junit4に対応　クラスパスを追加
    public Debugger create(String testClassName, String testMethodName) {
        String testBinDir = PropertyLoader.getProperty("d4jTestBinDir");
        String targetBinDir = PropertyLoader.getProperty("d4jTargetBinDir");
        return new Debugger("jisd.fl.util.TestLauncher " + testClassName + " " + testMethodName,
                "-cp " + "./build/classes/java/main" + ":" + testBinDir + ":" + targetBinDir
        + ":" + "/Users/ezaki/.gradle/caches/modules-2/files-2.1/org.junit.platform/junit-platform-launcher/1.10.0/89a1922534ed102be1fb2a8c0b2c6151297a12bf/junit-platform-launcher-1.10.0.jar"
        + ":" + "/Users/ezaki/.gradle/caches/modules-2/files-2.1/org.junit.platform/junit-platform-engine/1.10.0/276c4edcf64fabb5a139fa7b4f99330d7a93b804/junit-platform-engine-1.10.0.jar"
        + ":" + "/Users/ezaki/.gradle/caches/modules-2/files-2.1/org.junit.platform/junit-platform-commons/1.10.0/d533ff2c286eaf963566f92baf5f8a06628d2609/junit-platform-commons-1.10.0.jar"
        + ":" + "/Users/ezaki/.gradle/caches/modules-2/files-2.1/org.junit.jupiter/junit-jupiter-engine/5.10.0/90587932d718fc51a48112d33045a18476c542ad/junit-jupiter-engine-5.10.0.jar"
        + ":" + "/Users/ezaki/.gradle/caches/modules-2/files-2.1/org.junit.jupiter/junit-jupiter-api/5.10.0/2fe4ba3d31d5067878e468c96aa039005a9134d3/junit-jupiter-api-5.10.0.jar"
        + ":" + "/Users/ezaki/.gradle/caches/modules-2/files-2.1/org.opentest4j/opentest4j/1.3.0/152ea56b3a72f655d4fd677fc0ef2596c3dd5e6e/opentest4j-1.3.0.jar");
    }
}