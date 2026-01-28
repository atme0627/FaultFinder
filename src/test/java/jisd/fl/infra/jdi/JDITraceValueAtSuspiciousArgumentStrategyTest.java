package jisd.fl.infra.jdi;

import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousArgument;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * JDITraceValueAtSuspiciousArgumentStrategy の JDI integration test.
 *
 * 主なテスト観点: actualValue による実行の特定が正しく機能するか
 * - 同じメソッド呼び出しが複数回実行される場合、正しい実行を特定できるか
 * - 特定した実行時点での可視変数が正しく観測されるか
 */
@Execution(ExecutionMode.SAME_THREAD)
class JDITraceValueAtSuspiciousArgumentStrategyTest {

    private static final String FIXTURE_FQCN = "jisd.fl.fixture.ArgumentStrategyFixture";

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

    // ===== 単純な引数テスト =====

    @Test
    @Timeout(20)
    void simple_argument_observes_variables() throws Exception {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#simple_argument()");
        MethodElementName locateMethod = new MethodElementName(FIXTURE_FQCN + "#simple_argument()");
        MethodElementName calleeMethod = new MethodElementName(FIXTURE_FQCN + "#helper(int)");
        int targetLine = findMethodCallLine(locateMethod, "helper");

        List<TracedValue> result = traceArgument(testMethod, locateMethod, calleeMethod, targetLine, "10", 0);

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "x", "10"), "x=10 を観測できるべき: " + formatResult(result));
    }

    // ===== 複数の引数テスト =====

    @Test
    @Timeout(20)
    void multiple_arguments_observes_variables() throws Exception {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#multiple_arguments()");
        MethodElementName locateMethod = new MethodElementName(FIXTURE_FQCN + "#multiple_arguments()");
        MethodElementName calleeMethod = new MethodElementName(FIXTURE_FQCN + "#multiArg(int,int,int)");
        int targetLine = findMethodCallLine(locateMethod, "multiArg");

        // argIndex = 1 (2番目の引数 b=20)
        List<TracedValue> result = traceArgument(testMethod, locateMethod, calleeMethod, targetLine, "20", 1);

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "a", "10"), "a=10 を観測できるべき: " + formatResult(result));
        assertTrue(hasValue(result, "b", "20"), "b=20 を観測できるべき: " + formatResult(result));
        assertTrue(hasValue(result, "c", "30"), "c=30 を観測できるべき: " + formatResult(result));
    }

    // ===== ループ内でのメソッド呼び出しテスト =====

    @Test
    @Timeout(20)
    void loop_calling_method_identifies_first_execution() throws Exception {
        // actualValue = "0" で1回目の呼び出し (i=0) を特定
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#loop_calling_method()");
        MethodElementName locateMethod = new MethodElementName(FIXTURE_FQCN + "#loop_calling_method()");
        MethodElementName calleeMethod = new MethodElementName(FIXTURE_FQCN + "#increment(int)");
        int targetLine = findMethodCallLine(locateMethod, "increment");

        List<TracedValue> result = traceArgument(testMethod, locateMethod, calleeMethod, targetLine, "0", 0);

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "i", "0"), "i=0 を観測できるべき: " + formatResult(result));
    }

    @Test
    @Timeout(20)
    void loop_calling_method_identifies_third_execution() throws Exception {
        // actualValue = "2" で3回目の呼び出し (i=2) を特定
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#loop_calling_method()");
        MethodElementName locateMethod = new MethodElementName(FIXTURE_FQCN + "#loop_calling_method()");
        MethodElementName calleeMethod = new MethodElementName(FIXTURE_FQCN + "#increment(int)");
        int targetLine = findMethodCallLine(locateMethod, "increment");

        List<TracedValue> result = traceArgument(testMethod, locateMethod, calleeMethod, targetLine, "2", 0);

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "i", "2"), "i=2 を観測できるべき: " + formatResult(result));
    }

    // ===== 条件分岐テスト =====

    @Test
    @Timeout(20)
    void conditional_argument_true_path() throws Exception {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#conditional_argument_true_path()");
        MethodElementName locateMethod = new MethodElementName(FIXTURE_FQCN + "#conditionalCall(boolean)");
        MethodElementName calleeMethod = new MethodElementName(FIXTURE_FQCN + "#helper(int)");
        int targetLine = findMethodCallLineWithArg(locateMethod, "helper", "100");

        List<TracedValue> result = traceArgument(testMethod, locateMethod, calleeMethod, targetLine, "100", 0);

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "a", "50"), "a=50 を観測できるべき: " + formatResult(result));
    }

    @Test
    @Timeout(20)
    void conditional_argument_false_path() throws Exception {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#conditional_argument_false_path()");
        MethodElementName locateMethod = new MethodElementName(FIXTURE_FQCN + "#conditionalCall(boolean)");
        MethodElementName calleeMethod = new MethodElementName(FIXTURE_FQCN + "#helper(int)");
        int targetLine = findMethodCallLineWithArg(locateMethod, "helper", "200");

        List<TracedValue> result = traceArgument(testMethod, locateMethod, calleeMethod, targetLine, "200", 0);

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "a", "50"), "a=50 を観測できるべき: " + formatResult(result));
    }

    // ===== 式を引数に渡すテスト =====

    @Test
    @Timeout(20)
    void expression_argument_observes_variables() throws Exception {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#expression_argument()");
        MethodElementName locateMethod = new MethodElementName(FIXTURE_FQCN + "#expression_argument()");
        MethodElementName calleeMethod = new MethodElementName(FIXTURE_FQCN + "#helper(int)");
        int targetLine = findMethodCallLine(locateMethod, "helper");

        List<TracedValue> result = traceArgument(testMethod, locateMethod, calleeMethod, targetLine, "30", 0);

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "a", "10"), "a=10 を観測できるべき: " + formatResult(result));
        assertTrue(hasValue(result, "b", "20"), "b=20 を観測できるべき: " + formatResult(result));
    }

    // ===== コンストラクタの引数テスト =====

    @Test
    @Timeout(20)
    void constructor_argument_observes_variables() throws Exception {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#constructor_argument()");
        MethodElementName locateMethod = new MethodElementName(FIXTURE_FQCN + "#constructor_argument()");
        // コンストラクタの場合は FQCN を指定
        MethodElementName calleeMethod = new MethodElementName(FIXTURE_FQCN + "$SimpleClass#<init>(int)");
        int targetLine = findConstructorCallLine(locateMethod, "SimpleClass");

        List<TracedValue> result = traceArgument(testMethod, locateMethod, calleeMethod, targetLine, "42", 0);

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "value", "42"), "value=42 を観測できるべき: " + formatResult(result));
    }

    // ===== 同じ行で複数のメソッド呼び出しテスト =====

    @Test
    @Timeout(20)
    void multiple_calls_same_line_first() throws Exception {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#multiple_calls_same_line_first()");
        MethodElementName locateMethod = new MethodElementName(FIXTURE_FQCN + "#multiple_calls_same_line_first()");
        MethodElementName calleeMethod = new MethodElementName(FIXTURE_FQCN + "#helper(int)");
        int targetLine = findMethodCallLine(locateMethod, "helper");

        // actualValue = "5" で最初の helper(a) を特定
        List<TracedValue> result = traceArgument(testMethod, locateMethod, calleeMethod, targetLine, "5", 0);

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "a", "5"), "a=5 を観測できるべき: " + formatResult(result));
        assertTrue(hasValue(result, "b", "10"), "b=10 を観測できるべき: " + formatResult(result));
    }

    @Test
    @Timeout(20)
    void multiple_calls_same_line_second() throws Exception {
        MethodElementName testMethod = new MethodElementName(FIXTURE_FQCN + "#multiple_calls_same_line_second()");
        MethodElementName locateMethod = new MethodElementName(FIXTURE_FQCN + "#multiple_calls_same_line_second()");
        MethodElementName calleeMethod = new MethodElementName(FIXTURE_FQCN + "#helper(int)");
        int targetLine = findMethodCallLine(locateMethod, "helper");

        // actualValue = "10" で2番目の helper(b) を特定
        List<TracedValue> result = traceArgument(testMethod, locateMethod, calleeMethod, targetLine, "10", 0);

        assertFalse(result.isEmpty(), "結果が空であってはならない");
        assertTrue(hasValue(result, "a", "5"), "a=5 を観測できるべき: " + formatResult(result));
        assertTrue(hasValue(result, "b", "10"), "b=10 を観測できるべき: " + formatResult(result));
    }

    // ===== Helper methods =====

    private static List<TracedValue> traceArgument(
            MethodElementName testMethod, MethodElementName locateMethod,
            MethodElementName calleeMethod, int locateLine, String actualValue, int argIndex) {

        SuspiciousArgument suspArg = new SuspiciousArgument(
                testMethod, locateMethod, locateLine, actualValue,
                calleeMethod, argIndex,
                "", false, List.of(), List.of(), List.of(), 0);

        JDITraceValueAtSuspiciousArgumentStrategy strategy = new JDITraceValueAtSuspiciousArgumentStrategy();
        return strategy.traceAllValuesAtSuspExpr(suspArg);
    }

    private static int findMethodCallLine(MethodElementName method, String methodName) throws NoSuchFileException {
        BlockStmt bs = JavaParserUtils.extractBodyOfMethod(method);
        assertNotNull(bs, "method body is null: " + method);

        var found = bs.findAll(MethodCallExpr.class).stream()
                .filter(mc -> mc.getNameAsString().equals(methodName))
                .findFirst();

        assertTrue(found.isPresent(), "メソッド呼び出しが見つかりません: " + methodName + " in " + method);
        return found.get().getBegin().orElseThrow().line;
    }

    private static int findMethodCallLineWithArg(MethodElementName method, String methodName, String argValue) throws NoSuchFileException {
        BlockStmt bs = JavaParserUtils.extractBodyOfMethod(method);
        assertNotNull(bs, "method body is null: " + method);

        var found = bs.findAll(MethodCallExpr.class).stream()
                .filter(mc -> mc.getNameAsString().equals(methodName))
                .filter(mc -> mc.getArguments().stream().anyMatch(arg -> arg.toString().equals(argValue)))
                .findFirst();

        assertTrue(found.isPresent(), "メソッド呼び出しが見つかりません: " + methodName + "(" + argValue + ") in " + method);
        return found.get().getBegin().orElseThrow().line;
    }

    private static int findConstructorCallLine(MethodElementName method, String className) throws NoSuchFileException {
        BlockStmt bs = JavaParserUtils.extractBodyOfMethod(method);
        assertNotNull(bs, "method body is null: " + method);

        var found = bs.findAll(ObjectCreationExpr.class).stream()
                .filter(oc -> oc.getTypeAsString().equals(className))
                .findFirst();

        assertTrue(found.isPresent(), "コンストラクタ呼び出しが見つかりません: new " + className + " in " + method);
        return found.get().getBegin().orElseThrow().line;
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
