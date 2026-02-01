package jisd.fl.infra.jdi.testexec;

import com.sun.jdi.VirtualMachine;
import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.util.PropertyLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JDIDebugServerHandle の統合テスト。
 * 実際に JVM を起動し、TCP + JDWP 接続を検証する。
 */
@Timeout(30)
class JDIDebugServerHandleTest {

    @BeforeAll
    static void initProperty() {
        Dotenv dotenv = Dotenv.load();
        Path testProjectDir = Paths.get(dotenv.get("TEST_PROJECT_DIR"));
        var cfg = new PropertyLoader.ProjectConfig(
                testProjectDir,
                Path.of("src/main/java"),
                Path.of("src/test/java"),
                Path.of("build/classes/java/main"),
                Path.of("build/classes/java/test")
        );
        PropertyLoader.setProjectConfig(cfg);
    }

    @Test
    void start_and_close() throws IOException {
        try (JDIDebugServerHandle session = JDIDebugServerHandle.start()) {
            assertNotNull(session.vm(), "VirtualMachine should be attached");
        }
    }

    @Test
    void vm_is_connected() throws IOException {
        try (JDIDebugServerHandle session = JDIDebugServerHandle.start()) {
            VirtualMachine vm = session.vm();
            assertNotNull(vm.name(), "VM name should not be null");
        }
    }

    @Test
    void runTest_returns_result() throws IOException {
        try (JDIDebugServerHandle session = JDIDebugServerHandle.start()) {
            MethodElementName testMethod = new MethodElementName(
                    "org.sample.MinimumTest#CheckRunTestAndWatchVariable()");
            boolean passed = session.runTest(testMethod);
            // テスト結果はどちらでもよい（接続が動くことを確認）
            // ただし例外が飛ばないことが重要
        }
    }

    @Test
    void runTest_multiple_times_in_same_session() throws IOException {
        try (JDIDebugServerHandle session = JDIDebugServerHandle.start()) {
            MethodElementName testMethod = new MethodElementName(
                    "org.sample.MinimumTest#CheckRunTestAndWatchVariable()");

            session.runTest(testMethod);
            session.runTest(testMethod);
            // 2回目も例外なく実行できること
        }
    }

    @Test
    void cleanupEventRequests_does_not_throw() throws IOException {
        try (JDIDebugServerHandle session = JDIDebugServerHandle.start()) {
            // リクエストがない状態でも例外にならない
            session.cleanupEventRequests();
        }
    }
}
