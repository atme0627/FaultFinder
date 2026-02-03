package jisd.fl.infra.jdi;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousArgument;
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
 * JDISearchSuspiciousReturnsArgumentStrategy の JDI integration test.
 *
 * 主なテスト観点:
 * - 引数式内のメソッド呼び出しの戻り値を収集できるか
 * - actualValue (引数の値) で正しい実行を特定できるか
 * - 同じメソッドが複数回呼ばれるケースを正しく処理できるか
 */
@Execution(ExecutionMode.SAME_THREAD)
class JDISearchSuspiciousReturnsArgumentStrategyTest {

    private static final String FIXTURE_FQCN = "jisd.fl.fixture.SearchReturnsArgumentFixture";

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
        // target(helper(10)) で helper の戻り値 (20) を収集
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#single_method_arg()");
        int targetLine = findAssignLine(testMethod, "result", "target(helper(10))");
        MethodElementName callee = new MethodElementName(FIXTURE_FQCN + "#target(int)");

        List<SuspiciousExpression> result = searchReturns(
                testMethod, targetLine, callee, 0, "20",
                List.of(1), 2, true);

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        assertEquals(1, result.size(), "1つのメソッド呼び出しの戻り値を収集: " + formatResult(result));

        SuspiciousReturnValue ret = (SuspiciousReturnValue) result.get(0);
        assertEquals("20", ret.actualValue(), "helper(10) の戻り値は 20: " + formatResult(result));
    }

    // ===== 複数メソッド呼び出しテスト =====

    @Test
    @Timeout(20)
    void multiple_method_calls_collects_all_return_values() throws Exception {
        // target2(add(5) + multiply(3)) で add と multiply の両方の戻り値を収集
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#multiple_method_args()");
        int targetLine = findAssignLine(testMethod, "result", "target2(add(5) + multiply(3))");
        MethodElementName callee = new MethodElementName(FIXTURE_FQCN + "#target2(int)");

        List<SuspiciousExpression> result = searchReturns(
                testMethod, targetLine, callee, 0, "19",
                List.of(1, 2), 3, true);

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        assertEquals(2, result.size(), "2つのメソッド呼び出しの戻り値を収集: " + formatResult(result));

        assertTrue(hasReturnValue(result, "10"), "add(5) の戻り値 10 を収集: " + formatResult(result));
        assertTrue(hasReturnValue(result, "9"), "multiply(3) の戻り値 9 を収集: " + formatResult(result));
    }

    // ===== ループ内での引数テスト =====

    @Test
    @Timeout(20)
    void loop_identifies_first_execution() throws Exception {
        // actualValue = "0" で1回目のループ実行を特定
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#loop_with_method_arg()");
        int targetLine = findAssignLine(testMethod, "sum", "target3(compute(i))");
        MethodElementName callee = new MethodElementName(FIXTURE_FQCN + "#target3(int)");

        List<SuspiciousExpression> result = searchReturns(
                testMethod, targetLine, callee, 0, "0",
                List.of(1), 2, true);

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        assertEquals(1, result.size(), "compute の戻り値を収集: " + formatResult(result));

        SuspiciousReturnValue ret = (SuspiciousReturnValue) result.get(0);
        assertEquals("0", ret.actualValue(), "compute(0) の戻り値 0 を収集: " + formatResult(result));
    }

    @Test
    @Timeout(20)
    void loop_identifies_third_execution() throws Exception {
        // actualValue = "4" で3回目のループ実行を特定
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#loop_with_method_arg()");
        int targetLine = findAssignLine(testMethod, "sum", "target3(compute(i))");
        MethodElementName callee = new MethodElementName(FIXTURE_FQCN + "#target3(int)");

        List<SuspiciousExpression> result = searchReturns(
                testMethod, targetLine, callee, 0, "4",
                List.of(1), 2, true);

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        assertEquals(1, result.size(), "compute の戻り値を収集: " + formatResult(result));

        SuspiciousReturnValue ret = (SuspiciousReturnValue) result.get(0);
        assertEquals("4", ret.actualValue(), "compute(2) の戻り値 4 を収集: " + formatResult(result));
    }

    // ===== メソッド呼び出しを含まない引数テスト =====

    @Test
    @Timeout(20)
    void no_method_call_returns_empty() throws Exception {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#no_method_call_arg()");
        int targetLine = findAssignLine(testMethod, "result", "target4(a + b)");
        MethodElementName callee = new MethodElementName(FIXTURE_FQCN + "#target4(int)");

        List<SuspiciousExpression> result = searchReturns(
                testMethod, targetLine, callee, 0, "15",
                List.of(), 1, false);

        assertTrue(result.isEmpty(), "メソッド呼び出しがない場合は空のリストを返す");
    }

    // ===== 同じメソッドが2回呼ばれるテスト =====

    @Test
    @Timeout(20)
    void same_method_twice_in_arg_collects_both() throws Exception {
        // target5(twice(3) + twice(5)) で twice が2回呼ばれる
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#same_method_twice_in_arg()");
        int targetLine = findAssignLine(testMethod, "result", "target5(twice(3) + twice(5))");
        MethodElementName callee = new MethodElementName(FIXTURE_FQCN + "#target5(int)");

        List<SuspiciousExpression> result = searchReturns(
                testMethod, targetLine, callee, 0, "16",
                List.of(1, 2), 3, true);

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        assertEquals(2, result.size(), "twice が2回呼ばれ、両方の戻り値を収集: " + formatResult(result));

        assertTrue(hasReturnValue(result, "6"), "twice(3) の戻り値 6 を収集: " + formatResult(result));
        assertTrue(hasReturnValue(result, "10"), "twice(5) の戻り値 10 を収集: " + formatResult(result));
    }

    // ===== 同じメソッドがネストして呼ばれるテスト =====

    @Test
    @Timeout(20)
    void same_method_nested_collects_both() throws Exception {
        // target7(doubler(doubler(3))) で doubler が2回呼ばれる
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#same_method_nested()");
        int targetLine = findAssignLine(testMethod, "result", "target7(doubler(doubler(3)))");
        MethodElementName callee = new MethodElementName(FIXTURE_FQCN + "#target7(int)");

        List<SuspiciousExpression> result = searchReturns(
                testMethod, targetLine, callee, 0, "12",
                List.of(1, 2), 3, true);

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        assertEquals(2, result.size(), "doubler が2回呼ばれ、両方の戻り値を収集: " + formatResult(result));

        assertTrue(hasReturnValue(result, "6"), "doubler(3) の戻り値 6 を収集: " + formatResult(result));
        assertTrue(hasReturnValue(result, "12"), "doubler(6) の戻り値 12 を収集: " + formatResult(result));
    }

    // ===== callee メソッドがネストして呼ばれるテスト（既知の問題） =====

    @Test
    @Timeout(20)
    void nested_callee_collects_inner_return_values() throws Exception {
        // target8(helper2(target8(3))) で外側の target8 の引数は 8
        // 評価順: target8(3) → helper2(...) → target8(...)  → collectAtCounts=[1,2], invokeCallCount=3
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#nested_callee()");
        int targetLine = findAssignLine(testMethod, "result", "target8(helper2(target8(3)))");
        MethodElementName callee = new MethodElementName(FIXTURE_FQCN + "#target8(int)");

        List<SuspiciousExpression> result = searchReturns(
                testMethod, targetLine, callee, 0, "8",
                List.of(1, 2), 3, true);

        assertFalse(result.isEmpty(), "戻り値を収集できるべき");
        assertEquals(2, result.size(), "target8(3) と helper2 の戻り値を収集: " + formatResult(result));
        assertTrue(hasReturnValue(result, "4"), "target8(3) の戻り値 4 を収集: " + formatResult(result));
        assertTrue(hasReturnValue(result, "8"), "helper2(4) の戻り値 8 を収集: " + formatResult(result));
    }

    // ===== Helper methods =====

    private static List<SuspiciousExpression> searchReturns(
            MethodElementName testMethod, int locateLine,
            MethodElementName invokeMethodName, int argIndex,
            String actualValue, List<Integer> collectAtCounts,
            int invokeCallCount, boolean hasMethodCalling) {

        SuspiciousArgument suspArg = new SuspiciousArgument(
                testMethod, testMethod, locateLine, actualValue,
                invokeMethodName, argIndex,
                "", hasMethodCalling, List.of(), List.of(),
                collectAtCounts, invokeCallCount);

        JDISearchSuspiciousReturnsArgumentStrategy strategy =
                new JDISearchSuspiciousReturnsArgumentStrategy();
        return strategy.search(suspArg);
    }

    private static int findAssignLine(MethodElementName method, String var, String rhsLiteral)
            throws NoSuchFileException {
        BlockStmt bs = JavaParserUtils.extractBodyOfMethod(method);
        assertNotNull(bs, "method body is null: " + method);

        // += などの複合代入も含めて検索
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