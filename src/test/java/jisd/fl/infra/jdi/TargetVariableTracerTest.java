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
}