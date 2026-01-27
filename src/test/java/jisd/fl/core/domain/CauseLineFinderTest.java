package jisd.fl.core.domain;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.*;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.infra.javaparser.JavaParserUtils;
import org.junit.jupiter.api.AfterAll;
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
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();
    private static PropertyLoader.ProjectConfig original;

    private final CauseLineFinder finder = new CauseLineFinder();

    @BeforeAll
    static void setUpProjectConfigForFixtures() {
        original = PropertyLoader.getTargetProjectConfig();

        var cfg = new PropertyLoader.ProjectConfig(
                PROJECT_ROOT.resolve("src/test/resources/fixtures"),
                Path.of("exec/src/main/java"),
                Path.of("exec/src/main/java"),
                PROJECT_ROOT.resolve("build/classes/java/fixtureExec"),
                PROJECT_ROOT.resolve("build/classes/java/fixtureExec")
        );

        PropertyLoader.setProjectConfig(cfg);
    }

    @AfterAll
    static void restoreProjectConfig() {
        if (original != null) {
            PropertyLoader.setProjectConfig(original);
        }
    }

    // ===== Pattern 1a: 既存変数への代入 =====

    @Test
    @Timeout(20)
    void pattern1a_simple_assignment() throws Exception {
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#pattern1a_simple_assignment()");
        int expectedLine = findAssignLine(m, "x", "42");

        Optional<SuspiciousExpression> result = finder.find(localVar(m, "x", "42"));

        assertAssignmentAt(result, expectedLine, "cause line は " + expectedLine + " 行目であるべき");
    }

    @Test
    @Timeout(20)
    void pattern1a_multiple_assignments() throws Exception {
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#pattern1a_multiple_assignments()");
        int expectedLine = findAssignLine(m, "x", "42");

        Optional<SuspiciousExpression> result = finder.find(localVar(m, "x", "42"));

        assertAssignmentAt(result, expectedLine, "最後の代入行が cause line であるべき");
    }

    @Test
    @Timeout(20)
    void pattern1a_conditional_assignment() throws Exception {
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#pattern1a_conditional_assignment()");
        int expectedLine = findAssignLine(m, "x", "42");

        Optional<SuspiciousExpression> result = finder.find(localVar(m, "x", "42"));

        assertAssignmentAt(result, expectedLine, "条件分岐内の代入行が cause line であるべき");
    }

    // ===== Pattern 1b: 宣言時の初期化 =====

    @Test
    @Timeout(20)
    void pattern1b_declaration_with_initialization() throws Exception {
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#pattern1b_declaration_with_initialization()");
        int expectedLine = findLocalDeclLine(m, "x");

        Optional<SuspiciousExpression> result = finder.find(localVar(m, "x", "42"));

        assertAssignmentAt(result, expectedLine, "宣言と初期化が同時の行が cause line であるべき");
    }

    @Test
    @Timeout(20)
    void pattern1b_complex_expression_initialization() throws Exception {
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#pattern1b_complex_expression_initialization()");
        int expectedLine = findLocalDeclLine(m, "x");

        // Fixture: int a = 10; int b = 16; int x = a * 2 + b; → x = 36
        Optional<SuspiciousExpression> result = finder.find(localVar(m, "x", "36"));

        assertAssignmentAt(result, expectedLine, "複雑な式での初期化行が cause line であるべき");
    }

    // ===== Pattern 2-1: 引数由来（直接渡す） =====

    @Test
    @Timeout(20)
    void pattern2_1_literal_argument() throws Exception {
        MethodElementName caller = new MethodElementName(FIXTURE_FQCN + "#pattern2_1_literal_argument()");
        MethodElementName callee = new MethodElementName(FIXTURE_FQCN + "#calleeMethod(int)");

        Optional<SuspiciousExpression> result = finder.find(localVarWithCallee(caller, callee, "param", "42"));

        assertArgumentFound(result, "引数由来の場合 SuspiciousArgument であるべき");
    }

    @Test
    @Timeout(20)
    void pattern2_1_variable_argument() throws Exception {
        MethodElementName caller = new MethodElementName(FIXTURE_FQCN + "#pattern2_1_variable_argument()");
        MethodElementName callee = new MethodElementName(FIXTURE_FQCN + "#calleeMethod(int)");

        Optional<SuspiciousExpression> result = finder.find(localVarWithCallee(caller, callee, "param", "42"));

        assertArgumentFound(result, "SuspiciousArgument であるべき");
    }

    // ===== Pattern 2-2: 引数由来（汚染された変数を渡す） =====

    @Test
    @Timeout(20)
    void pattern2_2_contaminated_variable_as_argument() throws Exception {
        MethodElementName caller = new MethodElementName(FIXTURE_FQCN + "#pattern2_2_contaminated_variable_as_argument()");
        MethodElementName callee = new MethodElementName(FIXTURE_FQCN + "#calleeMethod(int)");

        Optional<SuspiciousExpression> result = finder.find(localVarWithCallee(caller, callee, "param", "42"));

        assertArgumentFound(result, "引数として渡された汚染変数も SuspiciousArgument として検出されるべき");
    }

    // ===== Field Pattern: フィールド変数への代入 =====

    private static final String FIELD_TARGET_FQCN = "jisd.fl.fixture.FieldTarget";

    @Test
    @Timeout(20)
    void field_pattern_modified_in_another_method() throws Exception {
        MethodElementName failedTest = new MethodElementName(FIXTURE_FQCN + "#field_pattern_modified_in_another_method()");
        MethodElementName setValueMethod = new MethodElementName(FIELD_TARGET_FQCN + "#setValue(int)");
        ClassElementName locateClass = new ClassElementName(FIELD_TARGET_FQCN);
        int expectedLine = findAssignLine(setValueMethod, "value", "v");

        SuspiciousFieldVariable sv = new SuspiciousFieldVariable(failedTest, locateClass, "value", "42", true);
        Optional<SuspiciousExpression> result = finder.find(sv);

        assertAssignmentAt(result, expectedLine, "setValue 内の代入行 (" + expectedLine + ") が cause line であるべき");
    }

    @Test
    @Timeout(20)
    void field_pattern_nested_method_calls() throws Exception {
        MethodElementName failedTest = new MethodElementName(FIXTURE_FQCN + "#field_pattern_nested_method_calls()");
        MethodElementName setValueMethod = new MethodElementName(FIELD_TARGET_FQCN + "#setValue(int)");
        ClassElementName locateClass = new ClassElementName(FIELD_TARGET_FQCN);
        int expectedLine = findAssignLine(setValueMethod, "value", "v");

        SuspiciousFieldVariable sv = new SuspiciousFieldVariable(failedTest, locateClass, "value", "42", true);
        Optional<SuspiciousExpression> result = finder.find(sv);

        assertAssignmentAt(result, expectedLine, "setValue 内の代入行 (" + expectedLine + ") が cause line であるべき");
    }

    // ===== Test helpers =====

    private SuspiciousLocalVariable localVar(MethodElementName method, String varName, String actual) {
        return new SuspiciousLocalVariable(method, method.toString(), varName, actual, true, false);
    }

    private SuspiciousLocalVariable localVarWithCallee(MethodElementName caller, MethodElementName callee, String varName, String actual) {
        return new SuspiciousLocalVariable(caller, callee.toString(), varName, actual, true, false);
    }

    private void assertAssignmentAt(Optional<SuspiciousExpression> result, int expectedLine, String message) {
        assertTrue(result.isPresent(), "cause line が見つかるべき");
        assertInstanceOf(SuspiciousAssignment.class, result.get(), "SuspiciousAssignment であるべき");
        SuspiciousAssignment assignment = (SuspiciousAssignment) result.get();
        assertEquals(expectedLine, assignment.locateLine, message);
    }

    private void assertArgumentFound(Optional<SuspiciousExpression> result, String message) {
        assertTrue(result.isPresent(), "cause line が見つかるべき");
        assertInstanceOf(SuspiciousArgument.class, result.get(), message);
    }

    // ===== AST helpers (行番号導出) =====

    private static int findLocalDeclLine(MethodElementName method, String var) throws NoSuchFileException {
        List<Integer> lines = JavaParserUtils.findLocalVariableDeclarationLine(method, var);
        assertFalse(lines.isEmpty(), "宣言行が見つからない: " + method + " var=" + var);
        return lines.get(0);
    }

    private static int findAssignLine(MethodElementName method, String var, String rhsLiteral) throws NoSuchFileException {
        BlockStmt bs = JavaParserUtils.extractBodyOfMethod(method);
        assertNotNull(bs, "メソッド本体が見つかりません: " + method);

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