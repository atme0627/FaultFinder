package jisd.fl.infra.jdi;

import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousArgument;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.infra.jdi.testexec.JDIDebugServerHandle;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JDISuspiciousArgumentsSearcher のテスト。
 * JDIDebugServerHandle 経由で JVM を共有し、引数検索の動作を検証する。
 * フィクスチャ: {@code jisd.fixture.JDIServerFixture}
 */
@Timeout(60)
class JDISuspiciousArgumentsSearcherTest {

    private static final String FIXTURE_CLASS = "jisd.fixture.JDIServerFixture";

    // argumentSearch テスト用
    private static final String FIXTURE_TEST_ARGUMENT_SEARCH = FIXTURE_CLASS + "#argumentSearch()";
    private static final int ARGUMENT_SEARCH_LINE = 47;

    // sameMethodMultipleCalls テスト用
    private static final String FIXTURE_TEST_SAME_METHOD = FIXTURE_CLASS + "#sameMethodMultipleCalls()";
    private static final int SAME_METHOD_LINE = 61;

    // differentMethodsOnSameLine テスト用
    private static final String FIXTURE_TEST_DIFFERENT_METHODS = FIXTURE_CLASS + "#differentMethodsOnSameLine()";
    private static final int DIFFERENT_METHODS_LINE = 74;

    // loopMultipleHits テスト用
    private static final String FIXTURE_TEST_LOOP = FIXTURE_CLASS + "#loopMultipleHits()";
    private static final int LOOP_LINE = 94;

    private static JDIDebugServerHandle session;
    private JDISuspiciousArgumentsSearcher searcher;

    @BeforeAll
    static void setUpClass() throws IOException {
        var cfg = new PropertyLoader.ProjectConfig(
                Path.of("/Users/ezaki/IdeaProjects/FaultFinder/src/test/resources/fixtures"),
                Path.of("exec/src/main/java"),
                Path.of("exec/src/main/java"),
                Path.of("/Users/ezaki/IdeaProjects/FaultFinder/build/classes/java/fixtureExec"),
                Path.of("/Users/ezaki/IdeaProjects/FaultFinder/build/classes/java/fixtureExec")
        );
        PropertyLoader.setProjectConfig(cfg);
        session = JDIDebugServerHandle.startShared();
    }

    @AfterAll
    static void tearDownClass() throws IOException {
        if (session != null) {
            session.close();
        }
    }

    @BeforeEach
    void setUp() {
        searcher = new JDISuspiciousArgumentsSearcher();
    }

    @Test
    void searchSuspiciousArgument_single_method_call() {
        // helperMethod(x) の x=10 を追跡
        MethodElementName failedTest = new MethodElementName(FIXTURE_TEST_ARGUMENT_SEARCH);
        MethodElementName helperMethodName = new MethodElementName(FIXTURE_CLASS + "#helperMethod(int)");
        MethodElementName callerMethod = new MethodElementName(FIXTURE_TEST_ARGUMENT_SEARCH);

        SuspiciousLocalVariable suspVar = new SuspiciousLocalVariable(
                failedTest,
                helperMethodName,
                "val",
                "10",
                true
        );

        Optional<SuspiciousArgument> result = searcher.searchSuspiciousArgument(suspVar, helperMethodName);

        assertTrue(result.isPresent(), "Should find the argument");
        SuspiciousArgument arg = result.get();
        assertAll("SuspiciousArgument fields",
                () -> assertEquals(failedTest, arg.failedTest(), "failedTest"),
                () -> assertEquals(callerMethod, arg.locateMethod(), "locateMethod"),
                () -> assertEquals(ARGUMENT_SEARCH_LINE, arg.locateLine(), "locateLine"),
                () -> assertEquals("10", arg.actualValue(), "actualValue"),
                () -> assertEquals(helperMethodName, arg.invokeMethodName, "invokeMethodName"),
                () -> assertEquals(0, arg.argIndex(), "argIndex"),
                () -> assertTrue(arg.stmtString().contains("helperMethod"), "stmtString should contain method call")
        );
    }

    @Test
    void searchSuspiciousArgument_same_method_multiple_calls() {
        // helper(1), helper(2), helper(3) で helper(2) を追跡
        MethodElementName failedTest = new MethodElementName(FIXTURE_TEST_SAME_METHOD);
        MethodElementName helperMethodName = new MethodElementName(FIXTURE_CLASS + "#helperIdentity(int)");
        MethodElementName callerMethod = new MethodElementName(FIXTURE_TEST_SAME_METHOD);

        SuspiciousLocalVariable suspVar = new SuspiciousLocalVariable(
                failedTest,
                helperMethodName,
                "val",
                "2",
                true
        );

        Optional<SuspiciousArgument> result = searcher.searchSuspiciousArgument(suspVar, helperMethodName);

        assertTrue(result.isPresent(), "Should find the argument");
        SuspiciousArgument arg = result.get();
        assertAll("SuspiciousArgument fields",
                () -> assertEquals(failedTest, arg.failedTest(), "failedTest"),
                () -> assertEquals(callerMethod, arg.locateMethod(), "locateMethod"),
                () -> assertEquals(SAME_METHOD_LINE, arg.locateLine(), "locateLine"),
                () -> assertEquals("2", arg.actualValue(), "actualValue"),
                () -> assertEquals(helperMethodName, arg.invokeMethodName, "invokeMethodName"),
                () -> assertEquals(0, arg.argIndex(), "argIndex"),
                () -> assertTrue(arg.stmtString().contains("helperIdentity"), "stmtString should contain method call"),
                // helper(2) は2番目の呼び出し、後に helper(3) が1回呼ばれる → invokeCallCount = 2
                () -> assertEquals(2, arg.invokeCallCount(), "invokeCallCount (2nd call)")
        );
    }

    @Test
    void searchSuspiciousArgument_different_methods_on_same_line() {
        // helperAdd(5) + helperMultiply(3) で helperMultiply を追跡
        MethodElementName failedTest = new MethodElementName(FIXTURE_TEST_DIFFERENT_METHODS);
        MethodElementName multiplyMethodName = new MethodElementName(FIXTURE_CLASS + "#helperMultiply(int)");
        MethodElementName callerMethod = new MethodElementName(FIXTURE_TEST_DIFFERENT_METHODS);

        SuspiciousLocalVariable suspVar = new SuspiciousLocalVariable(
                failedTest,
                multiplyMethodName,
                "x",
                "3",
                true
        );

        Optional<SuspiciousArgument> result = searcher.searchSuspiciousArgument(suspVar, multiplyMethodName);

        assertTrue(result.isPresent(), "Should find the argument");
        SuspiciousArgument arg = result.get();
        assertAll("SuspiciousArgument fields",
                () -> assertEquals(failedTest, arg.failedTest(), "failedTest"),
                () -> assertEquals(callerMethod, arg.locateMethod(), "locateMethod"),
                () -> assertEquals(DIFFERENT_METHODS_LINE, arg.locateLine(), "locateLine"),
                () -> assertEquals("3", arg.actualValue(), "actualValue"),
                () -> assertEquals(multiplyMethodName, arg.invokeMethodName, "invokeMethodName"),
                () -> assertEquals(0, arg.argIndex(), "argIndex"),
                () -> assertTrue(arg.stmtString().contains("helperMultiply"), "stmtString should contain method call"),
                // helperMultiply は2番目の呼び出し → invokeCallCount = 2
                () -> assertEquals(2, arg.invokeCallCount(), "invokeCallCount (2nd call)")
        );
    }

    @Test
    void searchSuspiciousArgument_loop_same_line_multiple_hits() {
        // ループで helperAccumulate(sum, i) が3回呼ばれる
        // i=2 の呼び出しを追跡（actualValue="2"）
        MethodElementName failedTest = new MethodElementName(FIXTURE_TEST_LOOP);
        MethodElementName accumulateMethodName = new MethodElementName(FIXTURE_CLASS + "#helperAccumulate(int,int)");
        MethodElementName callerMethod = new MethodElementName(FIXTURE_TEST_LOOP);

        SuspiciousLocalVariable suspVar = new SuspiciousLocalVariable(
                failedTest,
                accumulateMethodName,
                "val",
                "2",
                true
        );

        Optional<SuspiciousArgument> result = searcher.searchSuspiciousArgument(suspVar, accumulateMethodName);

        assertTrue(result.isPresent(), "Should find the argument for val=2");
        SuspiciousArgument arg = result.get();
        assertAll("SuspiciousArgument fields",
                () -> assertEquals(failedTest, arg.failedTest(), "failedTest"),
                () -> assertEquals(callerMethod, arg.locateMethod(), "locateMethod"),
                () -> assertEquals(LOOP_LINE, arg.locateLine(), "locateLine"),
                () -> assertEquals("2", arg.actualValue(), "actualValue"),
                () -> assertEquals(accumulateMethodName, arg.invokeMethodName, "invokeMethodName"),
                () -> assertEquals(1, arg.argIndex(), "argIndex (val is second argument)"),
                () -> assertTrue(arg.stmtString().contains("helperAccumulate"), "stmtString should contain method call"),
                // ループなので各反復で1回のメソッド呼び出し → invokeCallCount = 1
                () -> assertEquals(1, arg.invokeCallCount(), "invokeCallCount")
        );
    }

    @Test
    void searchSuspiciousArgument_no_matching_arg_returns_empty() {
        // 存在しない値で検索
        MethodElementName failedTest = new MethodElementName(FIXTURE_TEST_ARGUMENT_SEARCH);
        MethodElementName helperMethodName = new MethodElementName(FIXTURE_CLASS + "#helperMethod(int)");

        SuspiciousLocalVariable suspVar = new SuspiciousLocalVariable(
                failedTest,
                helperMethodName,
                "val",
                "999",
                true
        );

        Optional<SuspiciousArgument> result = searcher.searchSuspiciousArgument(suspVar, helperMethodName);

        assertTrue(result.isEmpty(), "Should return empty for non-matching value");
    }

    @Test
    void searchSuspiciousArgument_multiple_searches_in_same_session() {
        // 同一セッションで複数回検索
        MethodElementName failedTest = new MethodElementName(FIXTURE_TEST_ARGUMENT_SEARCH);
        MethodElementName helperMethodName = new MethodElementName(FIXTURE_CLASS + "#helperMethod(int)");
        MethodElementName callerMethod = new MethodElementName(FIXTURE_TEST_ARGUMENT_SEARCH);

        SuspiciousLocalVariable suspVar = new SuspiciousLocalVariable(
                failedTest,
                helperMethodName,
                "val",
                "10",
                true
        );

        // 1回目
        Optional<SuspiciousArgument> result1 = searcher.searchSuspiciousArgument(suspVar, helperMethodName);
        assertTrue(result1.isPresent(), "First search should succeed");
        assertAll("First search",
                () -> assertEquals(callerMethod, result1.get().locateMethod()),
                () -> assertEquals(ARGUMENT_SEARCH_LINE, result1.get().locateLine()),
                () -> assertEquals("10", result1.get().actualValue())
        );

        // 2回目
        Optional<SuspiciousArgument> result2 = searcher.searchSuspiciousArgument(suspVar, helperMethodName);
        assertTrue(result2.isPresent(), "Second search should succeed");
        assertAll("Second search",
                () -> assertEquals(callerMethod, result2.get().locateMethod()),
                () -> assertEquals(ARGUMENT_SEARCH_LINE, result2.get().locateLine()),
                () -> assertEquals("10", result2.get().actualValue())
        );

        // 3回目
        Optional<SuspiciousArgument> result3 = searcher.searchSuspiciousArgument(suspVar, helperMethodName);
        assertTrue(result3.isPresent(), "Third search should succeed");
        assertAll("Third search",
                () -> assertEquals(callerMethod, result3.get().locateMethod()),
                () -> assertEquals(ARGUMENT_SEARCH_LINE, result3.get().locateLine()),
                () -> assertEquals("10", result3.get().actualValue())
        );
    }
}
