package jisd.fl.probe.info;

import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.core.domain.port.SuspiciousArgumentsSearcher;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.infra.jdi.JDISuspiciousArgumentsSearcher;
import jisd.fl.util.PropertyLoader;
import jisd.fl.core.entity.MethodElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

class SuspiciousArgumentTest {
    final SuspiciousArgumentsSearcher searcher = new JDISuspiciousArgumentsSearcher();
    @BeforeEach
    void initProperty() {
        Dotenv dotenv = Dotenv.load();
        Path testProjectDir = Paths.get(dotenv.get("TEST_PROJECT_DIR"));
        PropertyLoader.setTargetSrcDir(testProjectDir.resolve("src/main/java").toString());
        PropertyLoader.setTestSrcDir(testProjectDir.resolve("src/test/java").toString());
    }

    @Test
    void searchSuspiciousArgument() {
        MethodElementName calleeMethodName = new MethodElementName("org.sample.util.Calc#methodCalling(int, int)");
        SuspiciousVariable suspVar = new SuspiciousVariable(
                new MethodElementName("org.sample.CalcTest#methodCall1()"),
                "org.sample.util.Calc#methodCalling(int, int)",
                "y",
                "3",
                true,
                false
        );

        SuspiciousArgument suspArg = searcher.searchSuspiciousArgument(suspVar, calleeMethodName).get();
        System.out.println(suspArg);
    }
}