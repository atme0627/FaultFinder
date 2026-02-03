package jisd.fl.infra.jdi;

import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousExpression;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * JDISearchSuspiciousReturnsReturnValueStrategy の JDI integration test.
 *
 * 主なテスト観点:
 * - return 式内のメソッド呼び出しの戻り値を収集できるか
 * - actualValue (return の戻り値) で正しい実行を特定できるか
 */
@Execution(ExecutionMode.SAME_THREAD)
class JDISearchSuspiciousReturnsReturnValueStrategyTest {

    private static final String FIXTURE_FQCN = "jisd.fixture.SearchReturnsReturnValueFixture";

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

    // ===== 単一メソッド呼び出しテスト =====

    @Test
    @Timeout(20)
    void single_method_call_collects_return_value() throws Exception {
        // return helper(10) で helper の戻り値 (20) を収集
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#single_method_call_return()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#singleMethodReturn()");
        int targetLine = findReturnLine(targetMethod, "helper(10)");

        // helper(10) returns 20, so return value is 20
        List<SuspiciousExpression> result = searchReturns(testMethod, targetMethod, targetLine, "20");

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        assertEquals(1, result.size(), "1つのメソッド呼び出しの戻り値を収集: " + formatResult(result));

        SuspiciousReturnValue ret = (SuspiciousReturnValue) result.get(0);
        assertEquals("20", ret.actualValue(), "helper(10) の戻り値は 20: " + formatResult(result));
    }

    // ===== 複数メソッド呼び出しテスト =====

    @Test
    @Timeout(20)
    void multiple_method_calls_collects_all_return_values() throws Exception {
        // return add(5) + multiply(3) で add と multiply の両方の戻り値を収集
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#multiple_method_calls_return()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#multipleMethodReturn()");
        int targetLine = findReturnLine(targetMethod, "add(5) + multiply(3)");

        // add(5) returns 10, multiply(3) returns 9, total = 19
        List<SuspiciousExpression> result = searchReturns(testMethod, targetMethod, targetLine, "19");

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        assertEquals(2, result.size(), "2つのメソッド呼び出しの戻り値を収集: " + formatResult(result));

        assertTrue(hasReturnValue(result, "10"), "add(5) の戻り値 10 を収集: " + formatResult(result));
        assertTrue(hasReturnValue(result, "9"), "multiply(3) の戻り値 9 を収集: " + formatResult(result));
    }

    // ===== ネストしたメソッド呼び出しテスト =====

    @Test
    @Timeout(20)
    void nested_method_call_collects_all_return_values() throws Exception {
        // return outer(inner(5)) で inner と outer の両方の戻り値を収集
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#nested_method_call_return()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#nestedMethodReturn()");
        int targetLine = findReturnLine(targetMethod, "outer(inner(5))");

        // inner(5) returns 10, outer(10) returns 30
        List<SuspiciousExpression> result = searchReturns(testMethod, targetMethod, targetLine, "30");

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        assertEquals(2, result.size(), "inner と outer の両方の戻り値を収集: " + formatResult(result));

        assertTrue(hasReturnValue(result, "10"), "inner(5) の戻り値 10 を収集: " + formatResult(result));
        assertTrue(hasReturnValue(result, "30"), "outer(10) の戻り値 30 を収集: " + formatResult(result));
    }

    // ===== ループ内での return テスト =====

    @Test
    @Timeout(20)
    void loop_identifies_first_execution() throws Exception {
        // actualValue = "0" で1回目のループ実行を特定
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#loop_calling_method_return()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#computeReturn(int)");
        int targetLine = findReturnLine(targetMethod, "compute(n)");

        List<SuspiciousExpression> result = searchReturns(testMethod, targetMethod, targetLine, "0");

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        assertEquals(1, result.size(), "compute の戻り値を収集: " + formatResult(result));

        SuspiciousReturnValue ret = (SuspiciousReturnValue) result.get(0);
        assertEquals("0", ret.actualValue(), "compute(0) の戻り値 0 を収集: " + formatResult(result));
    }

    @Test
    @Timeout(20)
    void loop_identifies_third_execution() throws Exception {
        // actualValue = "4" で3回目のループ実行を特定
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#loop_calling_method_return()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#computeReturn(int)");
        int targetLine = findReturnLine(targetMethod, "compute(n)");

        List<SuspiciousExpression> result = searchReturns(testMethod, targetMethod, targetLine, "4");

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        assertEquals(1, result.size(), "compute の戻り値を収集: " + formatResult(result));

        SuspiciousReturnValue ret = (SuspiciousReturnValue) result.get(0);
        assertEquals("4", ret.actualValue(), "compute(2) の戻り値 4 を収集: " + formatResult(result));
    }

    // ===== メソッド呼び出しを含まない return テスト =====

    @Test
    @Timeout(20)
    void no_method_call_returns_empty() throws Exception {
        // hasMethodCalling = false なので空のリストを返す
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#no_method_call_return()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#noMethodReturn()");
        int targetLine = findReturnLine(targetMethod, "a + b");

        List<SuspiciousExpression> result = searchReturns(testMethod, targetMethod, targetLine, "15", false);

        assertTrue(result.isEmpty(), "メソッド呼び出しがない場合は空のリストを返す");
    }

    // ===== Helper methods =====

    private static List<SuspiciousExpression> searchReturns(
            MethodElementName testMethod, MethodElementName targetMethod,
            int locateLine, String actualValue) {
        return searchReturns(testMethod, targetMethod, locateLine, actualValue, true);
    }

    private static List<SuspiciousExpression> searchReturns(
            MethodElementName testMethod, MethodElementName targetMethod,
            int locateLine, String actualValue, boolean hasMethodCalling) {

        SuspiciousReturnValue suspReturn = new SuspiciousReturnValue(
                testMethod, targetMethod, locateLine, actualValue,
                "", hasMethodCalling, List.of(), List.of());

        JDISearchSuspiciousReturnsReturnValueStrategy strategy =
                new JDISearchSuspiciousReturnsReturnValueStrategy();
        return strategy.search(suspReturn);
    }

    private static int findReturnLine(MethodElementName method, String returnExpr) throws NoSuchFileException {
        BlockStmt bs = JavaParserUtils.extractBodyOfMethod(method);
        assertNotNull(bs, "method body is null: " + method);

        var found = bs.findAll(ReturnStmt.class).stream()
                .filter(rs -> rs.getExpression().map(e -> e.toString().equals(returnExpr)).orElse(false))
                .findFirst();

        assertTrue(found.isPresent(), "return 文が見つかりません: return " + returnExpr + " in " + method);
        return found.get().getBegin().orElseThrow().line;
    }

    private static boolean hasReturnValue(List<SuspiciousExpression> result, String expectedValue) {
        return result.stream()
                .filter(e -> e instanceof SuspiciousReturnValue)
                .map(e -> (SuspiciousReturnValue) e)
                .anyMatch(rv -> rv.actualValue().equals(expectedValue));
    }

    private static String formatResult(List<SuspiciousExpression> result) {
        if (result.isEmpty()) return "[]";
        return result.stream()
                .map(e -> {
                    if (e instanceof SuspiciousReturnValue rv) {
                        return String.format("ReturnValue(%s=%s)", rv.locateMethod().shortMethodName(), rv.actualValue());
                    }
                    return e.toString();
                })
                .reduce((a, b) -> a + ", " + b)
                .orElse("[]");
    }
}