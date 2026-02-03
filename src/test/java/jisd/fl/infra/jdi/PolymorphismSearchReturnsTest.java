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
 * ポリモーフィズム（多態性）を含むメソッド呼び出しの戻り値追跡テスト。
 *
 * 主なテスト観点:
 * - インターフェース経由でのメソッド呼び出しの戻り値を収集できるか
 * - 異なる実装クラスが正しく追跡されるか
 * - ループ内でのポリモーフィズム
 */
@Execution(ExecutionMode.SAME_THREAD)
class PolymorphismSearchReturnsTest {

    private static final String FIXTURE_FQCN = "jisd.fl.fixture.PolymorphismFixture";

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

    // ===== 単一ポリモーフィズム呼び出しテスト =====

    @Test
    @Timeout(20)
    void polymorphism_single_call_collects_return_value() throws Exception {
        // Shape shape = new Circle(10); return shape.area();
        // Circle.area() returns 300 (3 * 10 * 10)
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#polymorphism_single_call()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#singlePolymorphismReturn()");
        int targetLine = findReturnLine(targetMethod, "shape.area()");

        List<SuspiciousExpression> result = searchReturns(testMethod, targetMethod, targetLine, "300");

        assertFalse(result.isEmpty(), "ポリモーフィズム呼び出しの戻り値を収集できるべき");
        assertEquals(1, result.size(), "1つのメソッド呼び出しの戻り値を収集: " + formatResult(result));

        SuspiciousReturnValue ret = (SuspiciousReturnValue) result.get(0);
        assertEquals("300", ret.actualValue(), "Circle.area() の戻り値は 300: " + formatResult(result));
        // ポリモーフィズムの本質: 実装クラス Circle のメソッドが追跡されること
        assertTrue(ret.locateMethod().fullyQualifiedClassName().endsWith("Circle"),
                "locateMethod は Circle クラスを指すべき: " + ret.locateMethod());
        assertEquals("area", ret.locateMethod().shortMethodName(),
                "メソッド名は area: " + ret.locateMethod());
    }

    // ===== ループ内でのポリモーフィズムテスト =====

    @Test
    @Timeout(20)
    void polymorphism_loop_identifies_circle_execution() throws Exception {
        // ループ内で Circle.area() が呼ばれた実行を特定
        // Circle(2).area() = 3 * 2 * 2 = 12
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#polymorphism_loop()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#computeArea(Shape)");
        int targetLine = findReturnLine(targetMethod, "s.area()");

        List<SuspiciousExpression> result = searchReturns(testMethod, targetMethod, targetLine, "12");

        assertFalse(result.isEmpty(), "ループ内ポリモーフィズムの戻り値を収集できるべき");
        assertEquals(1, result.size(), "Circle.area() の戻り値 12 を収集: " + formatResult(result));
        // ポリモーフィズムの本質: Circle クラスのメソッドが追跡されること
        assertTrue(hasReturnValueFromClass(result, "Circle", "12"),
                "Circle.area() の戻り値 12 を収集: " + formatResult(result));
    }

    @Test
    @Timeout(20)
    void polymorphism_loop_identifies_rectangle_execution() throws Exception {
        // ループ内で Rectangle.area() が呼ばれた実行を特定
        // Rectangle(4, 5).area() = 4 * 5 = 20
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#polymorphism_loop()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#computeArea(Shape)");
        int targetLine = findReturnLine(targetMethod, "s.area()");

        List<SuspiciousExpression> result = searchReturns(testMethod, targetMethod, targetLine, "20");

        assertFalse(result.isEmpty(), "ループ内ポリモーフィズムの戻り値を収集できるべき");
        assertEquals(1, result.size(), "Rectangle.area() の戻り値 20 を収集: " + formatResult(result));
        // ポリモーフィズムの本質: Rectangle クラスのメソッドが追跡されること
        assertTrue(hasReturnValueFromClass(result, "Rectangle", "20"),
                "Rectangle.area() の戻り値 20 を収集: " + formatResult(result));
    }

    // ===== ネストしたポリモーフィズム呼び出しテスト =====

    @Test
    @Timeout(20)
    void polymorphism_nested_collects_all_return_values() throws Exception {
        // return transform(shape.area());
        // Rectangle(5,6).area() = 30, transform(30) = 60
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#polymorphism_nested()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#nestedPolymorphismReturn()");
        int targetLine = findReturnLine(targetMethod, "transform(shape.area())");

        List<SuspiciousExpression> result = searchReturns(testMethod, targetMethod, targetLine, "60");

        assertFalse(result.isEmpty(), "ネストしたポリモーフィズムの戻り値を収集できるべき");
        assertEquals(2, result.size(), "area() と transform() の両方の戻り値を収集: " + formatResult(result));

        // Rectangle.area() が追跡されること（Shape.area() ではない）
        assertTrue(hasReturnValueFromClass(result, "Rectangle", "30"),
                "Rectangle.area() の戻り値 30 を収集: " + formatResult(result));
        assertTrue(hasReturnValue(result, "60"), "transform(30) の戻り値 60 を収集: " + formatResult(result));
    }

    // ===== 複数の Shape を組み合わせた return テスト =====

    @Test
    @Timeout(20)
    void polymorphism_multiple_in_return_collects_all() throws Exception {
        // return circle.area() + rectangle.area();
        // Circle(3).area() = 27, Rectangle(2,5).area() = 10, total = 37
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#polymorphism_multiple_in_return()");
        MethodElementName targetMethod = new MethodElementName(FIXTURE_FQCN + "#multiplePolymorphismReturn()");
        int targetLine = findReturnLine(targetMethod, "circle.area() + rectangle.area()");

        List<SuspiciousExpression> result = searchReturns(testMethod, targetMethod, targetLine, "37");

        assertFalse(result.isEmpty(), "複数のポリモーフィズム呼び出しの戻り値を収集できるべき");
        assertEquals(2, result.size(), "circle.area() と rectangle.area() の両方を収集: " + formatResult(result));

        // ポリモーフィズムの本質: 各実装クラスのメソッドが個別に追跡されること
        assertTrue(hasReturnValueFromClass(result, "Circle", "27"),
                "Circle.area() の戻り値 27 を収集: " + formatResult(result));
        assertTrue(hasReturnValueFromClass(result, "Rectangle", "10"),
                "Rectangle.area() の戻り値 10 を収集: " + formatResult(result));
    }

    // ===== Helper methods =====

    private static List<SuspiciousExpression> searchReturns(
            MethodElementName testMethod, MethodElementName targetMethod,
            int locateLine, String actualValue) {

        SuspiciousReturnValue suspReturn = new SuspiciousReturnValue(
                testMethod, targetMethod, locateLine, actualValue,
                "", true, List.of(), List.of());

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

    /**
     * 指定したクラス名と戻り値を持つ SuspiciousReturnValue が存在するか確認。
     * ポリモーフィズムの本質的な検証: 実装クラスのメソッドが正しく追跡されているか。
     */
    private static boolean hasReturnValueFromClass(
            List<SuspiciousExpression> result, String expectedClassName, String expectedValue) {
        return result.stream()
                .filter(e -> e instanceof SuspiciousReturnValue)
                .map(e -> (SuspiciousReturnValue) e)
                .anyMatch(rv -> rv.actualValue().equals(expectedValue)
                        && rv.locateMethod().fullyQualifiedClassName().endsWith(expectedClassName));
    }

    private static String formatResult(List<SuspiciousExpression> result) {
        if (result.isEmpty()) return "[]";
        return result.stream()
                .map(e -> {
                    if (e instanceof SuspiciousReturnValue rv) {
                        // クラス名も含めて表示（ポリモーフィズムの検証に重要）
                        String className = rv.locateMethod().fullyQualifiedClassName();
                        String shortClassName = className.contains("$")
                                ? className.substring(className.lastIndexOf("$") + 1)
                                : className.substring(className.lastIndexOf(".") + 1);
                        return String.format("ReturnValue(%s.%s=%s)",
                                shortClassName, rv.locateMethod().shortMethodName(), rv.actualValue());
                    }
                    return e.toString();
                })
                .reduce((a, b) -> a + ", " + b)
                .orElse("[]");
    }
}
