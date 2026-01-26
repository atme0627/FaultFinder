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
    void observes_pre_state_at_assignment_lines() throws Exception {
        // 対象：failedTest_for_tracing 内の x
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#failedTest_for_tracing()");

        // x=10 / x=20 の行番号を AST から取る（fixture の編集耐性UP）
        int lineAssign10 = findAssignLine(m, "x", "10");
        int lineAssign20 = findAssignLine(m, "x", "20");
        int lineDeclX = findLocalDeclLine(m, "x");

        SuspiciousVariable sv = new SuspiciousLocalVariable(m, m.toString(), "x", "20", true, false);

        TargetVariableTracer tracer = new TargetVariableTracer();
        List<TracedValue> traced = tracer.traceValuesOfTarget(sv);

        // 宣言行マーカー（現状仕様の固定）
        // ※宣言行で実測できない場合、"null" が入る設計だったのでそれを固定
        assertTrue(hasValueAtLine(traced, lineDeclX, "null") || hasAnyAtLine(traced, lineDeclX),
                "宣言行を踏んだことがトレースに現れるべき（現状仕様: null marker など）");

        // 行頭BPで pre-state を観測できることを固定
        assertTrue(hasValueAtLine(traced, lineAssign10, "0"),
                "x=10 行の行頭で x は代入前の 0 を観測できるべき。 line=" + lineAssign10);

        assertTrue(hasValueAtLine(traced, lineAssign20, "10"),
                "x=20 行の行頭で x は代入前の 10 を観測できるべき。 line=" + lineAssign20);
    }

    @Test
    @Timeout(20)
    void declaration_marker_case_is_recorded() throws Exception {
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#declaration_marker_case()");

        int lineDeclX = findLocalDeclLine(m, "x");
        int lineAssign = findAssignLine(m, "x", "10");

        SuspiciousVariable sv = new SuspiciousLocalVariable(m, m.toString(), "x", "20", true, false);

        TargetVariableTracer tracer = new TargetVariableTracer();
        List<TracedValue> traced = tracer.traceValuesOfTarget(sv);

        // 宣言行 marker の固定（現状仕様）
        assertTrue(hasValueAtLine(traced, lineDeclX, "null") || hasAnyAtLine(traced, lineDeclX),
                "宣言行を踏んだことがトレースに現れるべき。 line=" + lineDeclX);

        // 代入行でも一応何か観測できるはず、という“弱い”保証（揺れに強い）
        assertTrue(hasAnyAtLine(traced, lineAssign),
                "x=10 行で少なくとも1回は止まって観測が走るべき。 line=" + lineAssign);
    }

    @Test
    @Timeout(20)
    void multiple_statements_in_one_line_stops_before_first_statement() throws Exception {
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#multiple_statements_in_one_line()");

        // x = 1; x = 2; は同一行。AST上は AssignExpr が2つとも同じ begin line になるはず
        int sameLine = findAssignLine(m, "x", "1"); // 1 の代入行を代表として取る

        SuspiciousVariable sv = new SuspiciousLocalVariable(m, m.toString(), "x", "20", true, false);

        TargetVariableTracer tracer = new TargetVariableTracer();
        List<TracedValue> traced = tracer.traceValuesOfTarget(sv);

        // earliest codeIndex の Location に BP を張っているので、同一行なら「1つ目の代入の前」で止まる想定
        // その時点の x は 0 のはず（int x=0; の直後）
        assertTrue(hasValueAtLine(traced, sameLine, "0"),
                "同一行の最初の命令の前で止まるなら x は 0 を観測できるべき。 line=" + sameLine);
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
}