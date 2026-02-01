package jisd.fl.infra.jdi.testexec;

import com.sun.jdi.VirtualMachine;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.util.PropertyLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JDIDebugServerHandle の統合テスト。
 * 実際に JVM を起動し、TCP + JDWP 接続を検証する。
 * フィクスチャ: {@code jisd.fl.fixture.JDIServerFixture}
 */
@Timeout(30)
class JDIDebugServerHandleTest {

    private static final String FIXTURE_TEST =
            "jisd.fl.fixture.JDIServerFixture#simpleAssignment()";

    @BeforeAll
    static void initProperty() {
        var cfg = new PropertyLoader.ProjectConfig(
                Path.of("/Users/ezaki/IdeaProjects/FaultFinder/src/test/resources/fixtures"),
                Path.of("exec/src/main/java"),
                Path.of("exec/src/main/java"),
                Path.of("/Users/ezaki/IdeaProjects/FaultFinder/build/classes/java/fixtureExec"),
                Path.of("/Users/ezaki/IdeaProjects/FaultFinder/build/classes/java/fixtureExec")
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
            MethodElementName testMethod = new MethodElementName(FIXTURE_TEST);
            boolean passed = session.runTest(testMethod);
            assertTrue(passed, "simpleAssignment should pass");
        }
    }

    @Test
    void runTest_multiple_times_in_same_session() throws IOException {
        try (JDIDebugServerHandle session = JDIDebugServerHandle.start()) {
            MethodElementName testMethod = new MethodElementName(FIXTURE_TEST);

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