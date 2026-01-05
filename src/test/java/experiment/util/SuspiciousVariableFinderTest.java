package experiment.util;

import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestUtil;
import jisd.fl.core.entity.MethodElementName;
import org.junit.jupiter.api.Test;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

class SuspiciousVariableFinderTest {

    @Test
    void find1() throws NoSuchFileException {
        Dotenv dotenv = Dotenv.load();
        Path testProjectDir = Paths.get(dotenv.get("TEST_PROJECT_DIR"));
        PropertyLoader.setTargetSrcDir(testProjectDir.resolve("src/main/java").toString());
        PropertyLoader.setTestSrcDir(testProjectDir.resolve("src/test/java").toString());

        MethodElementName testMethodName = new MethodElementName("org.sample.coverage.ConditionalTest#testXEqualY()");

        TestUtil.compileForDebug(testMethodName);
        SuspiciousVariableFinder finder
                = new SuspiciousVariableFinder(testMethodName);
        List<SuspiciousVariable> result = finder.findSuspiciousVariableInAssertLine();
        result.forEach(vi -> System.out.println(vi.toString()));
    }

    @Test
    void find2() throws NoSuchFileException {
        Dotenv dotenv = Dotenv.load();
        Path testProjectDir = Paths.get(dotenv.get("TEST_PROJECT_DIR"));
        PropertyLoader.setTargetSrcDir(testProjectDir.resolve("src/main/java").toString());
        PropertyLoader.setTestSrcDir(testProjectDir.resolve("src/test/java").toString());

        MethodElementName testMethodName = new MethodElementName("experiment.util.internal.finder.LineValueWatcherTest#primitiveVariables()");

        TestUtil.compileForDebug(testMethodName);
        SuspiciousVariableFinder finder
                = new SuspiciousVariableFinder(testMethodName);
        List<SuspiciousVariable> result = finder.findSuspiciousVariableInAssertLine();
        result.forEach(vi -> System.out.println(vi.toString()));
    }
}