package jisd.fl.infra.jdi;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousAssignment;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
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
 * JDISearchSuspiciousReturnsAssignmentStrategy の JDI integration test.
 *
 * 主なテスト観点:
 * - 代入式の RHS（右辺）で呼ばれたメソッドの戻り値を収集できるか
 * - 直接呼び出しのみ収集し、間接呼び出しは除外されるか
 * - actualValue で正しい実行を特定できるか
 */
@Execution(ExecutionMode.SAME_THREAD)
class JDISearchSuspiciousReturnsAssignmentStrategyTest {

    private static final String FIXTURE_FQCN = "jisd.fixture.SearchReturnsAssignmentFixture";

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
        // x = helper(10) で helper の戻り値 (20) を収集
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#single_method_call()");
        int targetLine = findAssignLine(testMethod, "x", "helper(10)");

        List<SuspiciousExpression> result = searchReturns(testMethod, targetLine, "x", "20", true, List.of(1));

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        assertEquals(1, result.size(), "1つのメソッド呼び出しの戻り値を収集: " + formatResult(result));

        SuspiciousReturnValue ret = (SuspiciousReturnValue) result.get(0);
        assertEquals("20", ret.actualValue(), "helper(10) の戻り値は 20: " + formatResult(result));
    }

    // ===== 複数メソッド呼び出しテスト =====

    @Test
    @Timeout(20)
    void multiple_method_calls_collects_all_return_values() throws Exception {
        // x = add(5) + multiply(3) で add と multiply の両方の戻り値を収集
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#multiple_method_calls()");
        int targetLine = findAssignLine(testMethod, "x", "add(5) + multiply(3)");

        // add(5) returns 10, multiply(3) returns 9, total = 19
        List<SuspiciousExpression> result = searchReturns(testMethod, targetLine, "x", "19", true, List.of(1, 2));

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        assertEquals(2, result.size(), "2つのメソッド呼び出しの戻り値を収集: " + formatResult(result));

        // 収集された戻り値を確認
        assertTrue(hasReturnValue(result, "10"), "add(5) の戻り値 10 を収集: " + formatResult(result));
        assertTrue(hasReturnValue(result, "9"), "multiply(3) の戻り値 9 を収集: " + formatResult(result));
    }

    // ===== ネストしたメソッド呼び出しテスト =====

    @Test
    @Timeout(20)
    void nested_method_call_collects_outermost_return_value() throws Exception {
        // x = outer(inner(5)) で最外の outer のみの戻り値を収集
        // inner は outer の引数として ARGUMENT 追跡で発見される
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#nested_method_call()");
        int targetLine = findAssignLine(testMethod, "x", "outer(inner(5))");

        // eval order: inner(5)=1, outer(10)=2 → 最外の outer のみ収集
        List<SuspiciousExpression> result = searchReturns(testMethod, targetLine, "x", "30", true, List.of(2));

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        assertEquals(1, result.size(), "outer の戻り値のみを収集: " + formatResult(result));

        assertTrue(hasReturnValue(result, "30"), "outer(10) の戻り値 30 を収集: " + formatResult(result));
    }

    // ===== ループ内での代入テスト =====

    @Test
    @Timeout(20)
    void loop_identifies_first_execution() throws Exception {
        // actualValue = "0" で1回目のループ実行を特定
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#loop_with_method_call()");
        int targetLine = findAssignLine(testMethod, "x", "compute(i)");

        List<SuspiciousExpression> result = searchReturns(testMethod, targetLine, "x", "0", true, List.of(1));

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        assertEquals(1, result.size(), "compute の戻り値を収集: " + formatResult(result));

        SuspiciousReturnValue ret = (SuspiciousReturnValue) result.get(0);
        assertEquals("0", ret.actualValue(), "compute(0) の戻り値 0 を収集: " + formatResult(result));
    }

    @Test
    @Timeout(20)
    void loop_identifies_third_execution() throws Exception {
        // actualValue = "4" で3回目のループ実行を特定
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#loop_with_method_call()");
        int targetLine = findAssignLine(testMethod, "x", "compute(i)");

        List<SuspiciousExpression> result = searchReturns(testMethod, targetLine, "x", "4", true, List.of(1));

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        assertEquals(1, result.size(), "compute の戻り値を収集: " + formatResult(result));

        SuspiciousReturnValue ret = (SuspiciousReturnValue) result.get(0);
        assertEquals("4", ret.actualValue(), "compute(2) の戻り値 4 を収集: " + formatResult(result));
    }

    // ===== メソッド呼び出しを含まない代入テスト =====

    @Test
    @Timeout(20)
    void no_method_call_returns_empty() throws Exception {
        // hasMethodCalling = false なので空のリストを返す
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#no_method_call()");
        int targetLine = findAssignLine(testMethod, "x", "a + b");

        List<SuspiciousExpression> result = searchReturns(testMethod, targetLine, "x", "15", false, List.of());

        assertTrue(result.isEmpty(), "メソッド呼び出しがない場合は空のリストを返す");
    }

    // ===== 連鎖メソッド呼び出しテスト =====

    @Test
    @Timeout(20)
    void chained_method_calls_collects_return_values() throws Exception {
        // x = chainStart().getValue() で両方の戻り値を収集
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#chained_method_calls()");
        int targetLine = findAssignLine(testMethod, "x", "chainStart().getValue()");

        List<SuspiciousExpression> result = searchReturns(testMethod, targetLine, "x", "42", true, List.of(1, 2));

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        // chainStart() と getValue() の両方を収集
        assertEquals(2, result.size(), "連鎖呼び出しの戻り値を収集: " + formatResult(result));
    }

    // ===== Helper methods =====

    private static List<SuspiciousExpression> searchReturns(
            MethodElementName testMethod, int locateLine, String variableName,
            String actualValue, boolean hasMethodCalling,
            List<Integer> targetReturnCallPositions) {

        SuspiciousLocalVariable assignTarget = new SuspiciousLocalVariable(
                testMethod, testMethod, variableName, actualValue, true);

        SuspiciousAssignment suspAssign = new SuspiciousAssignment(
                testMethod, testMethod, locateLine, assignTarget,
                "", hasMethodCalling, List.of(), List.of(), targetReturnCallPositions);

        JDISearchSuspiciousReturnsAssignmentStrategy strategy =
                new JDISearchSuspiciousReturnsAssignmentStrategy();
        return strategy.search(suspAssign);
    }

    private static int findAssignLine(MethodElementName method, String var, String rhsLiteral)
            throws NoSuchFileException {
        BlockStmt bs = JavaParserUtils.extractBodyOfMethod(method);
        assertNotNull(bs, "method body is null: " + method);

        var found = bs.findAll(AssignExpr.class).stream()
                .filter(ae -> targetNameOf(ae.getTarget()).equals(var))
                .filter(ae -> ae.getValue().toString().equals(rhsLiteral))
                .findFirst();

        assertTrue(found.isPresent(),
                "代入行が見つかりません: " + var + " = " + rhsLiteral + " in " + method);
        return found.get().getBegin().orElseThrow().line;
    }

    private static String targetNameOf(com.github.javaparser.ast.expr.Expression target) {
        if (target.isArrayAccessExpr()) {
            return target.asArrayAccessExpr().getName().toString();
        } else if (target.isFieldAccessExpr()) {
            return target.asFieldAccessExpr().getName().toString();
        } else {
            return target.toString();
        }
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
