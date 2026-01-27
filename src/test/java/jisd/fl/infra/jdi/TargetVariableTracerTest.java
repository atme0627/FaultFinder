package jisd.fl.infra.jdi;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousFieldVariable;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.infra.javaparser.JavaParserUtils;
import org.junit.jupiter.api.BeforeAll;
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
 * TargetVariableTracer の JDI integration test.
 *
 * - createAt は揺れるので見ない
 * - 行番号は fixture の AST から導出して固定（ハードコードしない）
 * - JDI は並列実行しない
 */
@Execution(ExecutionMode.SAME_THREAD)
class TargetVariableTracerTest {

    private static final String FIXTURE_FQCN = "jisd.fl.fixture.TargetVariableTracerFixture";
    private static final String DUMMY_ACTUAL_VALUE = "999";
    private static final int LOOP_ITERATIONS = 3;
    private static PropertyLoader.ProjectConfig original;

    @BeforeAll
    static void setUpProjectConfigForFixtures() {
        original = PropertyLoader.getTargetProjectConfig();

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
    @Timeout(20)
    void observes_post_state_at_assignment_lines() throws Exception {
        // 基本的な代入行で post-state が観測できることを確認
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#failedTest_for_tracing()");
        int lineAssign10 = findAssignLine(m, "x", "10");
        int lineAssign20 = findAssignLine(m, "x", "20");
        int lineDeclX = findLocalDeclLine(m, "x");

        List<TracedValue> traced = traceVariable(m, "x");

        assertTrue(hasValueAtLine(traced, lineDeclX, "0"),
                assertMsg("宣言行 (int x = 0;) の実行後、x=0 を観測できるべき", lineDeclX, traced));
        assertTrue(hasValueAtLine(traced, lineAssign10, "10"),
                assertMsg("x=10 の実行後、x=10 を観測できるべき", lineAssign10, traced));
        assertTrue(hasValueAtLine(traced, lineAssign20, "20"),
                assertMsg("x=20 の実行後、x=20 を観測できるべき", lineAssign20, traced));
    }

    @Test
    @Timeout(20)
    void multiple_statements_in_one_line_observes_post_state() throws Exception {
        // 同一行に複数の文がある場合、最終的な post-state を観測
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#multiple_statements_in_one_line()");
        int sameLine = findAssignLine(m, "x", "1");

        List<TracedValue> traced = traceVariable(m, "x");

        assertTrue(hasValueAtLine(traced, sameLine, "2"),
                assertMsg("同一行 (x = 1; x = 2;) の実行後、x=2 を観測できるべき", sameLine, traced));
    }

    @Test
    @Timeout(20)
    void exception_stops_tracing_at_last_executed_line() throws Exception {
        // 例外で途中終了しても最後の実行行の post-state を観測
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#exception_case()");
        int lineDeclX = findLocalDeclLine(m, "x");
        int lineAssign10 = findAssignLine(m, "x", "10");

        List<TracedValue> traced = traceVariable(m, "x");

        assertTrue(hasValueAtLine(traced, lineDeclX, "0"),
                assertMsg("x=0 の実行後、x=0 を観測できるべき", lineDeclX, traced));
        assertTrue(hasValueAtLine(traced, lineAssign10, "10"),
                assertMsg("x=10 の実行後、x=10 を観測できるべき", lineAssign10, traced));
    }

    @Test
    @Timeout(20)
    void early_return_observes_executed_lines_only() throws Exception {
        // 早期リターンで実行された行のみ観測、到達しない行は観測されない
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#early_return_case()");
        int lineDeclX = findLocalDeclLine(m, "x");
        int lineAssign10 = findAssignLine(m, "x", "10");
        int lineAssign20 = findAssignLine(m, "x", "20");

        List<TracedValue> traced = traceVariable(m, "x");

        assertTrue(hasValueAtLine(traced, lineDeclX, "0"),
                assertMsg("x=0 の実行後、x=0 を観測できるべき", lineDeclX, traced));
        assertTrue(hasValueAtLine(traced, lineAssign10, "10"),
                assertMsg("x=10 の実行後、x=10 を観測できるべき", lineAssign10, traced));
        assertFalse(hasAnyAtLine(traced, lineAssign20),
                assertMsg("x=20 は到達しないため観測されないはず", lineAssign20, traced));
    }

    @Test
    @Timeout(20)
    void loop_observes_same_line_multiple_times() throws Exception {
        // ループ内の同じ行が複数回実行され、各回の post-state を観測
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#loop_case()");
        int lineLoop = findAssignLine(m, "x", "x + 1");

        List<TracedValue> traced = traceVariable(m, "x");

        long countAtLoopLine = traced.stream()
                .filter(tv -> tv.lineNumber == lineLoop)
                .count();

        assertEquals(LOOP_ITERATIONS, countAtLoopLine,
                String.format("ループ行 (line %d) は%d回観測されるべき。actual=%d, traced=%s",
                    lineLoop, LOOP_ITERATIONS, countAtLoopLine, formatTracedValues(traced)));

        assertTrue(hasValueAtLine(traced, lineLoop, "1"),
                assertMsg("1回目の実行後、x=1 を観測できるべき", lineLoop, traced));
        assertTrue(hasValueAtLine(traced, lineLoop, "2"),
                assertMsg("2回目の実行後、x=2 を観測できるべき", lineLoop, traced));
        assertTrue(hasValueAtLine(traced, lineLoop, "3"),
                assertMsg("3回目の実行後、x=3 を観測できるべき", lineLoop, traced));
    }

    @Test
    @Timeout(20)
    void conditional_branch_observes_taken_path_only() throws Exception {
        // 条件分岐で実行されたパスのみ観測、実行されないパスは観測されない
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#conditional_branch_case()");
        int lineAssign10 = findAssignLine(m, "x", "10");
        int lineAssign20 = findAssignLine(m, "x", "20");

        List<TracedValue> traced = traceVariable(m, "x");

        assertTrue(hasValueAtLine(traced, lineAssign10, "10"),
                assertMsg("x=10 の実行後、x=10 を観測できるべき", lineAssign10, traced));
        assertFalse(hasAnyAtLine(traced, lineAssign20),
                assertMsg("else ブロックの x=20 は実行されないため観測されないはず", lineAssign20, traced));
    }

    @Test
    @Timeout(20)
    void method_call_in_assignment_observes_post_state() throws Exception {
        // メソッド呼び出しを含む代入でも Step Over が正しく動作し post-state を観測
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#method_call_case()");
        int lineDecl = findLocalDeclLine(m, "x");

        List<TracedValue> traced = traceVariable(m, "x");

        assertTrue(hasValueAtLine(traced, lineDecl, "42"),
                assertMsg("x=helperMethod() の実行後、x=42 を観測できるべき", lineDecl, traced));
    }

    // -------------------------
    // AST helpers (行番号導出)
    // -------------------------

    private static int findLocalDeclLine(MethodElementName method, String var) throws NoSuchFileException {
        List<Integer> lines = JavaParserUtils.findLocalVariableDeclarationLine(method, var);
        assertFalse(lines.isEmpty(), "宣言行が見つからない: " + method + " var=" + var);
        // 通常1つのはず。複数なら最初を採用
        return lines.get(0);
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

    private static String targetNameOf(Expression target) {
        if (target.isArrayAccessExpr()) {
            return target.asArrayAccessExpr().getName().toString();
        } else if (target.isFieldAccessExpr()) {
            return target.asFieldAccessExpr().getName().toString();
        } else {
            return target.toString();
        }
    }

    // -------------------------
    // Trace helpers
    // -------------------------

    private static List<TracedValue> traceVariable(MethodElementName method, String variableName) {
        SuspiciousVariable sv = new SuspiciousLocalVariable(method, method.toString(),
                variableName, DUMMY_ACTUAL_VALUE, true, false);
        TargetVariableTracer tracer = new TargetVariableTracer();
        return tracer.traceValuesOfTarget(sv);
    }

    private static boolean hasValueAtLine(List<TracedValue> traced, int line, String expectedValue) {
        return traced.stream()
                .filter(tv -> tv.lineNumber == line)
                .anyMatch(tv -> expectedValue.equals(tv.value));
    }

    private static boolean hasAnyAtLine(List<TracedValue> traced, int line) {
        return traced.stream().anyMatch(tv -> tv.lineNumber == line);
    }

    private static String formatTracedValues(List<TracedValue> traced) {
        return traced.stream()
                .map(tv -> String.format("[L%d:%s]", tv.lineNumber, tv.value))
                .reduce((a, b) -> a + ", " + b)
                .orElse("[]");
    }

    private static String assertMsg(String description, int line, List<TracedValue> traced) {
        return String.format("%s (line %d)。traced=%s", description, line, formatTracedValues(traced));
    }

    // ===== フィールド変数用テスト =====
    // テスト対象クラス FieldTarget のフィールド value を追跡

    private static final String FIELD_TARGET_FQCN = "jisd.fl.fixture.FieldTarget";

    @Test
    @Timeout(20)
    void field_modified_in_another_method() throws Exception {
        // 別メソッド (setValue) でフィールドが変更されるケース
        MethodElementName failedTest = new MethodElementName(FIXTURE_FQCN + "#field_modified_in_another_method()");
        MethodElementName setValueMethod = new MethodElementName(FIELD_TARGET_FQCN + "#setValue(int)");
        int lineSetValue = findAssignLine(setValueMethod, "value", "v");

        List<TracedValue> traced = traceFieldVariable(failedTest, "value");

        assertTrue(hasValueAtLine(traced, lineSetValue, "42"),
                assertMsg("setValue(42) の実行後、value=42 を観測できるべき", lineSetValue, traced));
    }

    @Test
    @Timeout(20)
    void field_modified_across_multiple_methods() throws Exception {
        // 複数メソッドから連続してフィールドが変更されるケース
        MethodElementName failedTest = new MethodElementName(FIXTURE_FQCN + "#field_modified_across_multiple_methods()");
        MethodElementName initMethod = new MethodElementName(FIELD_TARGET_FQCN + "#initialize()");
        MethodElementName setValueMethod = new MethodElementName(FIELD_TARGET_FQCN + "#setValue(int)");
        MethodElementName incrementMethod = new MethodElementName(FIELD_TARGET_FQCN + "#increment()");

        int lineInit = findAssignLine(initMethod, "value", "0");
        int lineSetValue = findAssignLine(setValueMethod, "value", "v");
        int lineIncrement = findAssignLine(incrementMethod, "value", "this.value + 1");

        List<TracedValue> traced = traceFieldVariable(failedTest, "value");

        // initialize() で 0
        assertTrue(hasValueAtLine(traced, lineInit, "0"),
                assertMsg("initialize() の実行後、value=0 を観測できるべき", lineInit, traced));
        // setValue(10) で 10、その後 setValue(42) で 42
        assertTrue(hasValueAtLine(traced, lineSetValue, "10"),
                assertMsg("setValue(10) の実行後、value=10 を観測できるべき", lineSetValue, traced));
        assertTrue(hasValueAtLine(traced, lineSetValue, "42"),
                assertMsg("setValue(42) の実行後、value=42 を観測できるべき", lineSetValue, traced));
        // increment() で 11
        assertTrue(hasValueAtLine(traced, lineIncrement, "11"),
                assertMsg("increment() の実行後、value=11 を観測できるべき", lineIncrement, traced));
    }

    @Test
    @Timeout(20)
    void field_modified_in_nested_method_calls() throws Exception {
        // ネストしたメソッド呼び出しでフィールドが変更されるケース
        // prepareAndSet() が内部で initialize() と setValue() を呼ぶ
        MethodElementName failedTest = new MethodElementName(FIXTURE_FQCN + "#field_modified_in_nested_method_calls()");
        MethodElementName initMethod = new MethodElementName(FIELD_TARGET_FQCN + "#initialize()");
        MethodElementName setValueMethod = new MethodElementName(FIELD_TARGET_FQCN + "#setValue(int)");

        int lineInit = findAssignLine(initMethod, "value", "0");
        int lineSetValue = findAssignLine(setValueMethod, "value", "v");

        List<TracedValue> traced = traceFieldVariable(failedTest, "value");

        // prepareAndSet(42) 内の initialize() で 0
        assertTrue(hasValueAtLine(traced, lineInit, "0"),
                assertMsg("initialize() の実行後、value=0 を観測できるべき", lineInit, traced));
        // prepareAndSet(42) 内の setValue(42) で 42
        assertTrue(hasValueAtLine(traced, lineSetValue, "42"),
                assertMsg("setValue(42) の実行後、value=42 を観測できるべき", lineSetValue, traced));
    }

    // -------------------------
    // Field helpers
    // -------------------------

    private static List<TracedValue> traceFieldVariable(MethodElementName failedTest, String variableName) {
        ClassElementName locateClass = new ClassElementName(FIELD_TARGET_FQCN);
        SuspiciousVariable sv = new SuspiciousFieldVariable(failedTest, locateClass,
                variableName, DUMMY_ACTUAL_VALUE, true);
        TargetVariableTracer tracer = new TargetVariableTracer();
        return tracer.traceValuesOfTarget(sv);
    }
}