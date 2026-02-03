package jisd.fl.infra.junit;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.BreakpointEvent;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.infra.jdi.testexec.JDIDebugServerHandle;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SharedJUnitDebugger の統合テスト。
 * JDIDebugServerHandle 経由で JVM を共有し、複数回の execute() を検証する。
 * フィクスチャ: {@code jisd.fixture.JDIServerFixture}
 */
@Timeout(60)
class SharedJUnitDebuggerTest {

    private static final String FIXTURE_CLASS = "jisd.fixture.JDIServerFixture";
    private static final String FIXTURE_TEST_SIMPLE =
            FIXTURE_CLASS + "#simpleAssignment()";
    /** line 22: {@code x = 10;} */
    private static final int SIMPLE_ASSIGNMENT_BP_LINE = 22;

    private static JDIDebugServerHandle session;

    @BeforeAll
    static void setUp() throws IOException {
        var cfg = new PropertyLoader.ProjectConfig(
                Path.of("/Users/ezaki/IdeaProjects/FaultFinder/src/test/resources/fixtures"),
                Path.of("exec/src/main/java"),
                Path.of("exec/src/main/java"),
                Path.of("/Users/ezaki/IdeaProjects/FaultFinder/build/classes/java/fixtureExec"),
                Path.of("/Users/ezaki/IdeaProjects/FaultFinder/build/classes/java/fixtureExec")
        );
        PropertyLoader.setProjectConfig(cfg);
        session = JDIDebugServerHandle.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void execute_completes_without_exception() {
        MethodElementName testMethod = new MethodElementName(FIXTURE_TEST_SIMPLE);

        SharedJUnitDebugger debugger = new SharedJUnitDebugger(session, testMethod);
        debugger.setBreakpoints(FIXTURE_CLASS, List.of(SIMPLE_ASSIGNMENT_BP_LINE));
        debugger.execute();
    }

    @Test
    void execute_with_breakpoint_fires_handler() {
        MethodElementName testMethod = new MethodElementName(FIXTURE_TEST_SIMPLE);

        SharedJUnitDebugger debugger = new SharedJUnitDebugger(session, testMethod);

        List<Integer> hitLines = new ArrayList<>();
        debugger.registerEventHandler(BreakpointEvent.class, (vm, ev) -> {
            BreakpointEvent bpe = (BreakpointEvent) ev;
            hitLines.add(bpe.location().lineNumber());
        });

        debugger.setBreakpoints(FIXTURE_CLASS, List.of(SIMPLE_ASSIGNMENT_BP_LINE));
        debugger.execute();

        assertFalse(hitLines.isEmpty(), "Breakpoint handler should have been called");
        assertTrue(hitLines.contains(SIMPLE_ASSIGNMENT_BP_LINE));
    }

    @Test
    void execute_multiple_times_in_same_session() {
        MethodElementName testMethod = new MethodElementName(FIXTURE_TEST_SIMPLE);

        // 1回目
        SharedJUnitDebugger debugger1 = new SharedJUnitDebugger(session, testMethod);
        debugger1.setBreakpoints(FIXTURE_CLASS, List.of(SIMPLE_ASSIGNMENT_BP_LINE));
        debugger1.execute();

        // 2回目（同じセッションで新しい debugger）
        SharedJUnitDebugger debugger2 = new SharedJUnitDebugger(session, testMethod);
        debugger2.setBreakpoints(FIXTURE_CLASS, List.of(SIMPLE_ASSIGNMENT_BP_LINE));
        debugger2.execute();
    }

    @Test
    void execute_with_shouldStop() {
        MethodElementName testMethod = new MethodElementName(FIXTURE_TEST_SIMPLE);

        SharedJUnitDebugger debugger = new SharedJUnitDebugger(session, testMethod);

        List<Integer> hitLines = new ArrayList<>();
        debugger.registerEventHandler(BreakpointEvent.class, (vm, ev) -> {
            BreakpointEvent bpe = (BreakpointEvent) ev;
            hitLines.add(bpe.location().lineNumber());
        });

        debugger.setBreakpoints(FIXTURE_CLASS, List.of(SIMPLE_ASSIGNMENT_BP_LINE));
        debugger.execute(() -> !hitLines.isEmpty());

        assertFalse(hitLines.isEmpty());
    }

    @Test
    void close_is_noop() {
        MethodElementName testMethod = new MethodElementName(FIXTURE_TEST_SIMPLE);

        SharedJUnitDebugger debugger = new SharedJUnitDebugger(session, testMethod);
        // close() を呼んでもセッションは閉じない
        debugger.close();

        // セッションがまだ使える
        VirtualMachine vm = session.vm();
        assertNotNull(vm.name());
    }
}