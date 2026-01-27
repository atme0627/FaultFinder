package jisd.fl.infra.jdi;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.core.entity.element.MethodElementName;
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
        // 対象：failedTest_for_tracing 内の x
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#failedTest_for_tracing()");

        // x=10 / x=20 の行番号を AST から取る（fixture の編集耐性UP）
        int lineAssign10 = findAssignLine(m, "x", "10");
        int lineAssign20 = findAssignLine(m, "x", "20");
        int lineDeclX = findLocalDeclLine(m, "x");

        SuspiciousVariable sv = new SuspiciousLocalVariable(m, m.toString(), "x", "20", true, false);

        TargetVariableTracer tracer = new TargetVariableTracer();
        List<TracedValue> traced = tracer.traceValuesOfTarget(sv);

        // 宣言行の post-state を観測（int x = 0; の実行後）
        assertTrue(hasValueAtLine(traced, lineDeclX, "0"),
                "宣言行 (int x = 0;) の実行後、x は 0 を観測できるべき。 line=" + lineDeclX);

        // 各行の post-state を観測できることを固定
        assertTrue(hasValueAtLine(traced, lineAssign10, "10"),
                "x=10 行の実行後、x は 10 を観測できるべき。 line=" + lineAssign10);

        assertTrue(hasValueAtLine(traced, lineAssign20, "20"),
                "x=20 行の実行後、x は 20 を観測できるべき。 line=" + lineAssign20);
    }

    @Test
    @Timeout(20)
    void multiple_statements_in_one_line_observes_post_state() throws Exception {
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#multiple_statements_in_one_line()");

        // x = 1; x = 2; は同一行。AST上は AssignExpr が2つとも同じ begin line になるはず
        int sameLine = findAssignLine(m, "x", "1"); // 1 の代入行を代表として取る

        SuspiciousVariable sv = new SuspiciousLocalVariable(m, m.toString(), "x", "20", true, false);

        TargetVariableTracer tracer = new TargetVariableTracer();
        List<TracedValue> traced = tracer.traceValuesOfTarget(sv);

        // 同一行の post-state を観測（x = 1; x = 2; の実行後）
        // Step で次の行に進んだ時点では x = 2 になっているはず
        assertTrue(hasValueAtLine(traced, sameLine, "2"),
                "同一行 (x = 1; x = 2;) の実行後、x は 2 を観測できるべき。 line=" + sameLine);
    }

    @Test
    @Timeout(20)
    void exception_stops_tracing_at_last_executed_line() throws Exception {
        // 目的: 例外で途中終了した場合、最後に実行された行の post-state を確実に観測
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#exception_case()");

        int lineDeclX = findLocalDeclLine(m, "x");
        int lineAssign10 = findAssignLine(m, "x", "10");

        SuspiciousVariable sv = new SuspiciousLocalVariable(m, m.toString(), "x", "20", true, false);

        TargetVariableTracer tracer = new TargetVariableTracer();
        List<TracedValue> traced = tracer.traceValuesOfTarget(sv);

        // 評価基準: x=0 と x=10 の実行結果が観測されるべき
        assertTrue(hasValueAtLine(traced, lineDeclX, "0"),
                String.format("x=0 の実行後、x=0 を観測できるべき (line %d)。traced=%s",
                    lineDeclX, formatTracedValues(traced)));

        assertTrue(hasValueAtLine(traced, lineAssign10, "10"),
                String.format("x=10 の実行後、x=10 を観測できるべき (line %d)。traced=%s",
                    lineAssign10, formatTracedValues(traced)));
    }

    @Test
    @Timeout(20)
    void early_return_observes_executed_lines_only() throws Exception {
        // 目的: 早期リターンで終了した場合、実行された行のみ観測されることを確認
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#early_return_case()");

        int lineDeclX = findLocalDeclLine(m, "x");
        int lineAssign10 = findAssignLine(m, "x", "10");
        int lineAssign20 = findAssignLine(m, "x", "20");

        SuspiciousVariable sv = new SuspiciousLocalVariable(m, m.toString(), "x", "20", true, false);

        TargetVariableTracer tracer = new TargetVariableTracer();
        List<TracedValue> traced = tracer.traceValuesOfTarget(sv);

        // 評価基準: x=0 と x=10 は観測されるが、x=20 は到達しないため観測されない
        assertTrue(hasValueAtLine(traced, lineDeclX, "0"),
                String.format("x=0 の実行後、x=0 を観測できるべき (line %d)。traced=%s",
                    lineDeclX, formatTracedValues(traced)));

        assertTrue(hasValueAtLine(traced, lineAssign10, "10"),
                String.format("x=10 の実行後、x=10 を観測できるべき (line %d)。traced=%s",
                    lineAssign10, formatTracedValues(traced)));

        assertFalse(hasAnyAtLine(traced, lineAssign20),
                String.format("x=20 は到達しないため観測されないはず (line %d)。traced=%s",
                    lineAssign20, formatTracedValues(traced)));
    }

    @Test
    @Timeout(20)
    void loop_observes_same_line_multiple_times() throws Exception {
        // 目的: ループ内の同じ行が複数回実行された場合、それぞれの post-state を観測
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#loop_case()");

        int lineLoop = findAssignLine(m, "x", "x + 1");

        SuspiciousVariable sv = new SuspiciousLocalVariable(m, m.toString(), "x", "20", true, false);

        TargetVariableTracer tracer = new TargetVariableTracer();
        List<TracedValue> traced = tracer.traceValuesOfTarget(sv);

        // 評価基準: 同じ行で3回実行され、x=1, x=2, x=3 が観測されるべき
        long countAtLoopLine = traced.stream()
                .filter(tv -> tv.lineNumber == lineLoop)
                .count();

        assertEquals(3, countAtLoopLine,
                String.format("ループ行 (line %d) は3回観測されるべき。actual=%d, traced=%s",
                    lineLoop, countAtLoopLine, formatTracedValues(traced)));

        // 各イテレーションの値を確認
        assertTrue(hasValueAtLine(traced, lineLoop, "1"),
                String.format("1回目の実行後、x=1 を観測できるべき (line %d)", lineLoop));
        assertTrue(hasValueAtLine(traced, lineLoop, "2"),
                String.format("2回目の実行後、x=2 を観測できるべき (line %d)", lineLoop));
        assertTrue(hasValueAtLine(traced, lineLoop, "3"),
                String.format("3回目の実行後、x=3 を観測できるべき (line %d)", lineLoop));
    }

    @Test
    @Timeout(20)
    void conditional_branch_observes_taken_path_only() throws Exception {
        // 目的: 条件分岐で実行されたパスのみが観測されることを確認
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#conditional_branch_case()");

        int lineAssign10 = findAssignLine(m, "x", "10");
        int lineAssign20 = findAssignLine(m, "x", "20");

        SuspiciousVariable sv = new SuspiciousLocalVariable(m, m.toString(), "x", "20", true, false);

        TargetVariableTracer tracer = new TargetVariableTracer();
        List<TracedValue> traced = tracer.traceValuesOfTarget(sv);

        // 評価基準: if ブロックの x=10 は実行されるが、else ブロックの x=20 は実行されない
        assertTrue(hasValueAtLine(traced, lineAssign10, "10"),
                String.format("x=10 の実行後、x=10 を観測できるべき (line %d)。traced=%s",
                    lineAssign10, formatTracedValues(traced)));

        assertFalse(hasAnyAtLine(traced, lineAssign20),
                String.format("else ブロックの x=20 は実行されないため観測されないはず (line %d)。traced=%s",
                    lineAssign20, formatTracedValues(traced)));
    }

    @Test
    @Timeout(20)
    void method_call_in_assignment_observes_post_state() throws Exception {
        // 目的: メソッド呼び出しを含む代入でも post-state が正しく観測されることを確認
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#method_call_case()");

        int lineDecl = findLocalDeclLine(m, "x");

        SuspiciousVariable sv = new SuspiciousLocalVariable(m, m.toString(), "x", "20", true, false);

        TargetVariableTracer tracer = new TargetVariableTracer();
        List<TracedValue> traced = tracer.traceValuesOfTarget(sv);

        // 評価基準: helperMethod() の戻り値 42 が x に代入された後の状態が観測される
        assertTrue(hasValueAtLine(traced, lineDecl, "42"),
                String.format("x=helperMethod() の実行後、x=42 を観測できるべき (line %d)。traced=%s",
                    lineDecl, formatTracedValues(traced)));
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

        assertTrue(found.isPresent(), "代入行が見つかりません。: " + var + " = " + rhsLiteral + " in " + method);
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
}