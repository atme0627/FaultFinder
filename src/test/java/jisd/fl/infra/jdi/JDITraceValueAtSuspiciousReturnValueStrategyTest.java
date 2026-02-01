package jisd.fl.infra.jdi;

import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousReturnValue;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.infra.jdi.testexec.JDIDebugServerHandle;
import jisd.fl.infra.javaparser.JavaParserUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JDITraceValueAtSuspiciousReturnValueStrategy の JDI integration test.
 *
 * 主なテスト観点: actualValue による実行の特定が正しく機能するか
 * - 同じ return 文が複数回実行される場合、正しい実行を特定できるか
 * - 特定した実行時点での可視変数が正しく観測されるか
 */
@Execution(ExecutionMode.SAME_THREAD)
class JDITraceValueAtSuspiciousReturnValueStrategyTest {

    private static final String FIXTURE_FQCN = "jisd.fl.fixture.TraceValueReturnValueFixture";

    private static JDIDebugServerHandle session;

    @BeforeAll
    static void setUpProjectConfigForFixtures() throws Exception {
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
    static void tearDown() throws Exception {
        if (session != null) { session.close(); session = null; }
    }

    @BeforeEach
    void cleanupBeforeEach() {
        if (session != null) session.cleanupEventRequests();
    }

    // ===== 単純な return テスト =====

    @Test
    @Timeout(20)
    void simple_return_observes_variables() throws Exception {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#simple_return()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#simpleReturn()");
        int targetLine = findReturnLine(targetMethod, "10");

        List<TracedValue> result = traceReturnValue(testMethod, targetMethod, targetLine, "10");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "a", "5"), "a=5 を観測できるべき: " + formatResult(result));
    }

    // ===== 式を含む return テスト =====

    @Test
    @Timeout(20)
    void expression_return_observes_variables() throws Exception {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#expression_return()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#expressionReturn()");
        int targetLine = findReturnLine(targetMethod, "a + b");

        List<TracedValue> result = traceReturnValue(testMethod, targetMethod, targetLine, "15");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "a", "5"), "a=5 を観測できるべき: " + formatResult(result));
        assertTrue(hasValue(result, "b", "10"), "b=10 を観測できるべき: " + formatResult(result));
    }

    // ===== メソッド呼び出しを含む return テスト =====

    @Test
    @Timeout(20)
    void method_call_return_observes_variables() throws Exception {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#method_call_return()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#methodCallReturn()");
        int targetLine = findReturnLine(targetMethod, "doubleValue(y)");

        List<TracedValue> result = traceReturnValue(testMethod, targetMethod, targetLine, "42");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "y", "21"), "y=21 を観測できるべき: " + formatResult(result));
    }

    // ===== ループから呼び出されるメソッドの return テスト =====

    @Test
    @Timeout(20)
    void loop_calling_method_identifies_first_execution() throws Exception {
        // actualValue = "1" で1回目の呼び出し (i=0) を特定
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#loop_calling_method()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#incrementReturn(int)");
        int targetLine = findReturnLine(targetMethod, "x + 1");

        List<TracedValue> result = traceReturnValue(testMethod, targetMethod, targetLine, "1");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "x", "0"), "x=0 を観測できるべき: " + formatResult(result));
    }

    @Test
    @Timeout(20)
    void loop_calling_method_identifies_third_execution() throws Exception {
        // actualValue = "3" で3回目の呼び出し (i=2) を特定
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#loop_calling_method()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#incrementReturn(int)");
        int targetLine = findReturnLine(targetMethod, "x + 1");

        List<TracedValue> result = traceReturnValue(testMethod, targetMethod, targetLine, "3");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "x", "2"), "x=2 を観測できるべき: " + formatResult(result));
    }

    // ===== 条件分岐テスト =====

    @Test
    @Timeout(20)
    void conditional_return_true_path() throws Exception {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#conditional_return_true_path()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#conditionalReturn(boolean)");
        int targetLine = findReturnLine(targetMethod, "10");

        List<TracedValue> result = traceReturnValue(testMethod, targetMethod, targetLine, "10");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "a", "5"), "a=5 を観測できるべき: " + formatResult(result));
    }

    @Test
    @Timeout(20)
    void conditional_return_false_path() throws Exception {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#conditional_return_false_path()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#conditionalReturn(boolean)");
        int targetLine = findReturnLine(targetMethod, "20");

        List<TracedValue> result = traceReturnValue(testMethod, targetMethod, targetLine, "20");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "a", "5"), "a=5 を観測できるべき: " + formatResult(result));
    }

    // ===== 変数を返す return テスト =====

    @Test
    @Timeout(20)
    void variable_return_observes_all_variables() throws Exception {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#variable_return()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#variableReturn()");
        int targetLine = findReturnLine(targetMethod, "x");

        List<TracedValue> result = traceReturnValue(testMethod, targetMethod, targetLine, "30");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "a", "10"), "a=10 を観測できるべき: " + formatResult(result));
        assertTrue(hasValue(result, "b", "20"), "b=20 を観測できるべき: " + formatResult(result));
        assertTrue(hasValue(result, "x", "30"), "x=30 を観測できるべき: " + formatResult(result));
    }

    // ===== Helper methods =====

    private static List<TracedValue> traceReturnValue(
            MethodElementName testMethod, MethodElementName targetMethod,
            int locateLine, String actualValue) {

        SuspiciousReturnValue suspReturn = new SuspiciousReturnValue(
                testMethod, targetMethod, locateLine, actualValue,
                "", false, List.of(), List.of());

        JDITraceValueAtSuspiciousReturnValueStrategy strategy = new JDITraceValueAtSuspiciousReturnValueStrategy();
        return strategy.traceAllValuesAtSuspExpr(suspReturn);
    }

    private static int findReturnLine(MethodElementName method, String returnExpr) throws NoSuchFileException {
        BlockStmt bs = JavaParserUtils.extractBodyOfMethod(method);
        assertNotNull(bs, "method body is null: " + method);

        Optional<ReturnStmt> found = bs.findAll(ReturnStmt.class).stream()
                .filter(rs -> rs.getExpression().map(e -> e.toString().equals(returnExpr)).orElse(false))
                .findFirst();

        assertTrue(found.isPresent(), "return 文が見つかりません: return " + returnExpr + " in " + method);
        return found.get().getBegin().orElseThrow().line;
    }

    private static boolean hasValue(List<TracedValue> result, String variableName, String expectedValue) {
        return result.stream()
                .anyMatch(tv -> tv.variableName.equals(variableName) && tv.value.equals(expectedValue));
    }

    private static String formatResult(List<TracedValue> result) {
        if (result.isEmpty()) return "[]";
        return result.stream()
                .map(tv -> String.format("%s=%s", tv.variableName, tv.value))
                .reduce((a, b) -> a + ", " + b)
                .orElse("[]");
    }
}