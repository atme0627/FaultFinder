package jisd.fl.core.domain;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.*;
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
 * CauseLineFinder の統合テスト
 *
 * post-state 観測に対応した TargetVariableTracer を使用して、
 * CauseLineFinder が正しく cause line を特定できることを確認する
 */
@Execution(ExecutionMode.SAME_THREAD)
class CauseLineFinderTest {

    private static final String FIXTURE_FQCN = "jisd.fl.fixture.CauseLineFinderFixture";
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

    // ===== Pattern 1a: 既存変数への代入 =====

    @Test
    @Timeout(20)
    void pattern1a_simple_assignment() throws Exception {
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#pattern1a_simple_assignment()");
        int expectedLine = findAssignLine(m, "x", "42");

        SuspiciousLocalVariable sv = new SuspiciousLocalVariable(m, m.toString(), "x", "42", true, false);
        CauseLineFinder finder = new CauseLineFinder();
        Optional<SuspiciousExpression> result = finder.find(sv);

        assertTrue(result.isPresent(), "cause line が見つかるべき");
        assertTrue(result.get() instanceof SuspiciousAssignment, "SuspiciousAssignment であるべき");
        SuspiciousAssignment assignment = (SuspiciousAssignment) result.get();
        assertEquals(expectedLine, assignment.locateLine,
                String.format("cause line は %d 行目であるべき", expectedLine));
    }

    @Test
    @Timeout(20)
    void pattern1a_multiple_assignments() throws Exception {
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#pattern1a_multiple_assignments()");
        int expectedLine = findAssignLine(m, "x", "42");

        SuspiciousLocalVariable sv = new SuspiciousLocalVariable(m, m.toString(), "x", "42", true, false);
        CauseLineFinder finder = new CauseLineFinder();
        Optional<SuspiciousExpression> result = finder.find(sv);

        assertTrue(result.isPresent(), "cause line が見つかるべき");
        assertTrue(result.get() instanceof SuspiciousAssignment);
        SuspiciousAssignment assignment = (SuspiciousAssignment) result.get();
        assertEquals(expectedLine, assignment.locateLine,
                "最後の代入行が cause line であるべき");
    }

    @Test
    @Timeout(20)
    void pattern1a_conditional_assignment() throws Exception {
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#pattern1a_conditional_assignment()");
        int expectedLine = findAssignLine(m, "x", "42");

        SuspiciousLocalVariable sv = new SuspiciousLocalVariable(m, m.toString(), "x", "42", true, false);
        CauseLineFinder finder = new CauseLineFinder();
        Optional<SuspiciousExpression> result = finder.find(sv);

        assertTrue(result.isPresent(), "cause line が見つかるべき");
        assertTrue(result.get() instanceof SuspiciousAssignment);
        SuspiciousAssignment assignment = (SuspiciousAssignment) result.get();
        assertEquals(expectedLine, assignment.locateLine,
                "条件分岐内の代入行が cause line であるべき");
    }

    // ===== Pattern 1b: 宣言時の初期化 =====

    @Test
    @Timeout(20)
    void pattern1b_declaration_with_initialization() throws Exception {
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#pattern1b_declaration_with_initialization()");
        int expectedLine = findLocalDeclLine(m, "x");

        SuspiciousLocalVariable sv = new SuspiciousLocalVariable(m, m.toString(), "x", "42", true, false);
        CauseLineFinder finder = new CauseLineFinder();
        Optional<SuspiciousExpression> result = finder.find(sv);

        assertTrue(result.isPresent(), "cause line が見つかるべき");
        assertTrue(result.get() instanceof SuspiciousAssignment);
        SuspiciousAssignment assignment = (SuspiciousAssignment) result.get();
        assertEquals(expectedLine, assignment.locateLine,
                "宣言と初期化が同時の行が cause line であるべき");
    }

    @Test
    @Timeout(20)
    void pattern1b_complex_expression_initialization() throws Exception {
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#pattern1b_complex_expression_initialization()");
        int expectedLine = findLocalDeclLine(m, "x");

        SuspiciousLocalVariable sv = new SuspiciousLocalVariable(m, m.toString(), "x", "42", true, false);
        CauseLineFinder finder = new CauseLineFinder();
        Optional<SuspiciousExpression> result = finder.find(sv);

        assertTrue(result.isPresent(), "cause line が見つかるべき");
        assertTrue(result.get() instanceof SuspiciousAssignment);
        SuspiciousAssignment assignment = (SuspiciousAssignment) result.get();
        assertEquals(expectedLine, assignment.locateLine,
                "複雑な式での初期化行が cause line であるべき");
    }

    // ===== Pattern 2-1: 引数由来（直接渡す） =====

    @Test
    @Timeout(20)
    void pattern2_1_literal_argument() throws Exception {
        MethodElementName caller = new MethodElementName(FIXTURE_FQCN + "#pattern2_1_literal_argument()");
        MethodElementName callee = new MethodElementName(FIXTURE_FQCN + "#calleeMethod(int)");

        // calleeMethod 内の param が suspicious variable
        SuspiciousLocalVariable sv = new SuspiciousLocalVariable(callee, caller.toString(), "param", "42", true, false);
        CauseLineFinder finder = new CauseLineFinder();
        Optional<SuspiciousExpression> result = finder.find(sv);

        assertTrue(result.isPresent(), "cause line が見つかるべき");
        assertTrue(result.get() instanceof SuspiciousArgument,
                "引数由来の場合 SuspiciousArgument であるべき");
    }

    @Test
    @Timeout(20)
    void pattern2_1_variable_argument() throws Exception {
        MethodElementName caller = new MethodElementName(FIXTURE_FQCN + "#pattern2_1_variable_argument()");
        MethodElementName callee = new MethodElementName(FIXTURE_FQCN + "#calleeMethod(int)");

        SuspiciousLocalVariable sv = new SuspiciousLocalVariable(callee, caller.toString(), "param", "42", true, false);
        CauseLineFinder finder = new CauseLineFinder();
        Optional<SuspiciousExpression> result = finder.find(sv);

        assertTrue(result.isPresent(), "cause line が見つかるべき");
        assertTrue(result.get() instanceof SuspiciousArgument);
    }

    // ===== Pattern 2-2: 引数由来（汚染された変数を渡す） =====

    @Test
    @Timeout(20)
    void pattern2_2_contaminated_variable_as_argument() throws Exception {
        MethodElementName caller = new MethodElementName(FIXTURE_FQCN + "#pattern2_2_contaminated_variable_as_argument()");
        MethodElementName callee = new MethodElementName(FIXTURE_FQCN + "#calleeMethod(int)");

        // calleeMethod 内の param が suspicious variable
        SuspiciousLocalVariable sv = new SuspiciousLocalVariable(callee, caller.toString(), "param", "42", true, false);
        CauseLineFinder finder = new CauseLineFinder();
        Optional<SuspiciousExpression> result = finder.find(sv);

        assertTrue(result.isPresent(), "cause line が見つかるべき");
        assertTrue(result.get() instanceof SuspiciousArgument,
                "引数として渡された汚染変数も SuspiciousArgument として検出されるべき");
    }

    // ===== AST helpers (行番号導出) =====

    private static int findLocalDeclLine(MethodElementName method, String var) throws NoSuchFileException {
        List<Integer> lines = JavaParserUtils.findLocalVariableDeclarationLine(method, var);
        assertFalse(lines.isEmpty(), "宣言行が見つからない: " + method + " var=" + var);
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
}