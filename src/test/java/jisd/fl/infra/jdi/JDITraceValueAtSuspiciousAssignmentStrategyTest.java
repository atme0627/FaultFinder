package jisd.fl.infra.jdi;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousAssignment;
import jisd.fl.core.entity.susp.SuspiciousFieldVariable;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.infra.javaparser.JavaParserUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JDITraceValueAtSuspiciousAssignmentStrategy の JDI integration test.
 *
 * 主なテスト観点: actualValue による実行の特定が正しく機能するか
 * - 同じ行が複数回実行される場合、正しい実行を特定できるか
 * - 特定した実行時点での可視変数が正しく観測されるか
 */
@Execution(ExecutionMode.SAME_THREAD)
class JDITraceValueAtSuspiciousAssignmentStrategyTest {

    private static final String FIXTURE_FQCN = "jisd.fl.fixture.TraceValueAssignmentFixture";
    private static final String FIELD_TARGET_FQCN = "jisd.fl.fixture.FieldTarget";

    @BeforeAll
    static void setUpProjectConfigForFixtures() {
        var cfg = new PropertyLoader.ProjectConfig(
                Path.of("/Users/ezaki/IdeaProjects/FaultFinder/src/test/resources/fixtures"),
                Path.of("exec/src/main/java"),
                Path.of("exec/src/main/java"),
                Path.of("/Users/ezaki/IdeaProjects/FaultFinder/build/classes/java/fixtureExec"),
                Path.of("/Users/ezaki/IdeaProjects/FaultFinder/build/classes/java/fixtureExec")
        );
        PropertyLoader.setProjectConfig(cfg);
    }

    // ===== ループ内の代入テスト =====

    @Test
    @Timeout(20)
    void loop_identifies_first_execution_by_actualValue() throws Exception {
        // actualValue = "1" で1回目のループ実行を特定
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#loop_same_line_multiple_executions()");
        int targetLine = findAssignLine(testMethod, "x", "x + 1");

        List<TracedValue> result = traceAssignment(testMethod, targetLine, "x", "1");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        // 1回目のループ: i=0, x=0 の時点で観測
        assertTrue(hasValue(result, "i", "0"), "i=0 を観測できるべき: " + formatResult(result));
        assertTrue(hasValue(result, "x", "0"), "x=0 を観測できるべき: " + formatResult(result));
    }

    @Test
    @Timeout(20)
    void loop_identifies_third_execution_by_actualValue() throws Exception {
        // actualValue = "3" で3回目のループ実行を特定
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#loop_same_line_multiple_executions()");
        int targetLine = findAssignLine(testMethod, "x", "x + 1");

        List<TracedValue> result = traceAssignment(testMethod, targetLine, "x", "3");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        // 3回目のループ: i=2, x=2 の時点で観測
        assertTrue(hasValue(result, "i", "2"), "i=2 を観測できるべき: " + formatResult(result));
        assertTrue(hasValue(result, "x", "2"), "x=2 を観測できるべき: " + formatResult(result));
    }

    @Test
    @Timeout(20)
    void loop_different_values_identifies_correct_execution() throws Exception {
        // actualValue = "10" で2回目のループ実行を特定 (i=1, x=i*10=10)
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#loop_different_values()");
        int targetLine = findAssignLine(testMethod, "x", "i * 10");

        List<TracedValue> result = traceAssignment(testMethod, targetLine, "x", "10");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        // 2回目のループ: i=1 の時点で観測
        assertTrue(hasValue(result, "i", "1"), "i=1 を観測できるべき: " + formatResult(result));
    }

    @Test
    @Timeout(20)
    void loop_compound_assignment_identifies_correct_execution() throws Exception {
        // actualValue = "20" で2回目のループ実行を特定 (x: 10→20)
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#loop_compound_assignment()");
        int targetLine = findCompoundAssignLine(testMethod, "x", "+=");

        List<TracedValue> result = traceAssignment(testMethod, targetLine, "x", "20");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        // 2回目のループ: i=1, x=10 の時点で観測
        assertTrue(hasValue(result, "i", "1"), "i=1 を観測できるべき: " + formatResult(result));
        assertTrue(hasValue(result, "x", "10"), "x=10 を観測できるべき: " + formatResult(result));
    }

    // ===== 同一行に複数代入テスト =====
    // 既知の制限: JDI のブレークポイントは行単位のため、同じ行の複数操作を区別できない

    @Disabled("JDI の制限: 同じ行の複数代入を区別できない")
    @Test
    @Timeout(20)
    void same_line_multiple_assignments_identifies_first() throws Exception {
        // actualValue = "1" で1回目の代入を特定
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#same_line_multiple_assignments()");
        int targetLine = findAssignLine(testMethod, "x", "1");

        List<TracedValue> result = traceAssignment(testMethod, targetLine, "x", "1");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        // 1回目の代入前: x=0
        assertTrue(hasValue(result, "x", "0"), "x=0 を観測できるべき: " + formatResult(result));
    }

    @Disabled("JDI の制限: 同じ行の複数代入を区別できない")
    @Test
    @Timeout(20)
    void same_line_multiple_assignments_identifies_second() throws Exception {
        // actualValue = "2" で2回目の代入を特定
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#same_line_multiple_assignments()");
        int targetLine = findAssignLine(testMethod, "x", "2");

        List<TracedValue> result = traceAssignment(testMethod, targetLine, "x", "2");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        // 2回目の代入前: x=1
        assertTrue(hasValue(result, "x", "1"), "x=1 を観測できるべき: " + formatResult(result));
    }

    // ===== 条件分岐テスト =====

    @Test
    @Timeout(20)
    void conditional_true_path_returns_result() throws Exception {
        // condition=true で x=10 の行が実行される
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#conditional_assignment_true_path()");
        int targetLine = findAssignLine(testMethod, "x", "10");

        List<TracedValue> result = traceAssignment(testMethod, targetLine, "x", "10");

        assertFalse(result.isEmpty(), "実行されるパスなので結果があるべき: " + formatResult(result));
        assertTrue(hasValue(result, "x", "0"), "代入前の x=0 を観測できるべき: " + formatResult(result));
    }

    @Test
    @Timeout(20)
    void conditional_false_path_returns_result() throws Exception {
        // condition=false で x=20 の行が実行される
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#conditional_assignment_false_path()");
        int targetLine = findAssignLine(testMethod, "x", "20");

        List<TracedValue> result = traceAssignment(testMethod, targetLine, "x", "20");

        assertFalse(result.isEmpty(), "実行されるパスなので結果があるべき: " + formatResult(result));
        assertTrue(hasValue(result, "x", "0"), "代入前の x=0 を観測できるべき: " + formatResult(result));
    }

    // ===== メソッド呼び出しを含む代入 =====

    @Test
    @Timeout(20)
    void loop_with_method_call_identifies_correct_execution() throws Exception {
        // actualValue = "4" で3回目のループ実行を特定 (compute(2) = 4)
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#loop_with_method_call()");
        int targetLine = findAssignLine(testMethod, "x", "compute(i)");

        List<TracedValue> result = traceAssignment(testMethod, targetLine, "x", "4");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        // 3回目のループ: i=2 の時点で観測
        assertTrue(hasValue(result, "i", "2"), "i=2 を観測できるべき: " + formatResult(result));
    }

    // ===== フィールドへの代入テスト =====

    @Test
    @Timeout(20)
    void field_multiple_assignments_identifies_first() throws Exception {
        // actualValue = "10" で1回目の setValue(10) を特定
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#field_multiple_assignments()");
        MethodElementName setValueMethod = new MethodElementName(FIELD_TARGET_FQCN + "#setValue(int)");
        int targetLine = findAssignLine(setValueMethod, "value", "v");

        List<TracedValue> result = traceFieldAssignment(testMethod, setValueMethod, targetLine, "value", "10");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        // setValue(10) 呼び出し時: v=10 を観測
        assertTrue(hasValue(result, "v", "10"), "v=10 を観測できるべき: " + formatResult(result));
    }

    @Test
    @Timeout(20)
    void field_multiple_assignments_identifies_second() throws Exception {
        // actualValue = "42" で2回目の setValue(42) を特定
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#field_multiple_assignments()");
        MethodElementName setValueMethod = new MethodElementName(FIELD_TARGET_FQCN + "#setValue(int)");
        int targetLine = findAssignLine(setValueMethod, "value", "v");

        List<TracedValue> result = traceFieldAssignment(testMethod, setValueMethod, targetLine, "value", "42");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        // setValue(42) 呼び出し時: v=42 を観測
        assertTrue(hasValue(result, "v", "42"), "v=42 を観測できるべき: " + formatResult(result));
    }

    @Test
    @Timeout(20)
    void field_loop_increment_identifies_second_execution() throws Exception {
        // actualValue = "2" で2回目の increment() を特定 (value: 1→2)
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#field_loop_increment()");
        MethodElementName incrementMethod = new MethodElementName(FIELD_TARGET_FQCN + "#increment()");
        int targetLine = findAssignLine(incrementMethod, "value", "this.value + 1");

        List<TracedValue> result = traceFieldAssignment(testMethod, incrementMethod, targetLine, "value", "2");

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        // 2回目の increment: this.value=1 の時点で観測
        assertTrue(hasValue(result, "value", "1"), "this.value=1 を観測できるべき: " + formatResult(result));
    }

    // ===== Helper methods =====

    private static List<TracedValue> traceAssignment(
            MethodElementName testMethod, int locateLine, String variableName, String actualValue) {

        SuspiciousVariable assignTarget = new SuspiciousLocalVariable(
                testMethod, testMethod.toString(), variableName, actualValue, true, false);

        SuspiciousAssignment suspAssign = new SuspiciousAssignment(
                testMethod, testMethod, locateLine, assignTarget, "", false, List.of(), List.of());

        JDITraceValueAtSuspiciousAssignmentStrategy strategy = new JDITraceValueAtSuspiciousAssignmentStrategy();
        return strategy.traceAllValuesAtSuspExpr(suspAssign);
    }

    private static List<TracedValue> traceFieldAssignment(
            MethodElementName testMethod, MethodElementName locateMethod,
            int locateLine, String variableName, String actualValue) {

        SuspiciousVariable assignTarget = new SuspiciousFieldVariable(
                testMethod, locateMethod.classElementName, variableName, actualValue, true);

        SuspiciousAssignment suspAssign = new SuspiciousAssignment(
                testMethod, locateMethod, locateLine, assignTarget, "", false, List.of(), List.of());

        JDITraceValueAtSuspiciousAssignmentStrategy strategy = new JDITraceValueAtSuspiciousAssignmentStrategy();
        return strategy.traceAllValuesAtSuspExpr(suspAssign);
    }

    private static int findAssignLine(MethodElementName method, String var, String rhsLiteral) throws NoSuchFileException {
        BlockStmt bs = JavaParserUtils.extractBodyOfMethod(method);
        assertNotNull(bs, "method body is null: " + method);

        Optional<AssignExpr> found = bs.findAll(AssignExpr.class).stream()
                .filter(ae -> targetNameOf(ae.getTarget()).equals(var))
                .filter(ae -> ae.getValue().toString().equals(rhsLiteral))
                .findFirst();

        assertTrue(found.isPresent(), "代入行が見つかりません: " + var + " = " + rhsLiteral + " in " + method);
        return found.get().getBegin().orElseThrow().line;
    }

    private static int findCompoundAssignLine(MethodElementName method, String var, String operator) throws NoSuchFileException {
        BlockStmt bs = JavaParserUtils.extractBodyOfMethod(method);
        assertNotNull(bs, "method body is null: " + method);

        Optional<AssignExpr> found = bs.findAll(AssignExpr.class).stream()
                .filter(ae -> targetNameOf(ae.getTarget()).equals(var))
                .filter(ae -> ae.getOperator().asString().equals(operator))
                .findFirst();

        assertTrue(found.isPresent(), "複合代入行が見つかりません: " + var + " " + operator + " in " + method);
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