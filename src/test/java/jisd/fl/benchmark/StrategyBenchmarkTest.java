package jisd.fl.benchmark;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.*;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.infra.javaparser.JavaParserUtils;
import jisd.fl.infra.jdi.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JDI Strategy のベンチマーク。
 * 各 Strategy の実行時間を計測し、高速化の効果測定に使用する。
 */
@Execution(ExecutionMode.SAME_THREAD)
class StrategyBenchmarkTest {

    private static final Logger logger = LoggerFactory.getLogger(StrategyBenchmarkTest.class);

    private static final String ASSIGN_FQCN = "jisd.fl.fixture.SearchReturnsAssignmentFixture";
    private static final String RETURN_FQCN = "jisd.fl.fixture.SearchReturnsReturnValueFixture";
    private static final String ARG_FQCN = "jisd.fl.fixture.SearchReturnsArgumentFixture";
    private static final String TRACE_ASSIGN_FQCN = "jisd.fl.fixture.TraceValueAssignmentFixture";
    private static final String TRACE_RETURN_FQCN = "jisd.fl.fixture.TraceValueReturnValueFixture";
    private static final String TRACE_ARG_FQCN = "jisd.fl.fixture.TraceValueArgumentFixture";

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

    // ===== SearchReturns: Assignment =====

    @Test
    @Timeout(60)
    void bench_search_returns_assignment_single() throws Exception {
        MethodElementName testMethod = new MethodElementName(ASSIGN_FQCN + "#single_method_call()");
        int targetLine = findAssignLine(testMethod, "x", "helper(10)");

        long start = System.nanoTime();
        List<SuspiciousExpression> result = searchReturnsAssignment(testMethod, targetLine, "x", "20", true);
        long elapsed = System.nanoTime() - start;

        assertFalse(result.isEmpty());
        printBench("SearchReturns/Assignment (single)", elapsed);
    }

    @Test
    @Timeout(60)
    void bench_search_returns_assignment_multiple() throws Exception {
        MethodElementName testMethod = new MethodElementName(ASSIGN_FQCN + "#multiple_method_calls()");
        int targetLine = findAssignLine(testMethod, "x", "add(5) + multiply(3)");

        long start = System.nanoTime();
        List<SuspiciousExpression> result = searchReturnsAssignment(testMethod, targetLine, "x", "19", true);
        long elapsed = System.nanoTime() - start;

        assertFalse(result.isEmpty());
        printBench("SearchReturns/Assignment (multiple)", elapsed);
    }

    // ===== SearchReturns: ReturnValue =====

    @Test
    @Timeout(60)
    void bench_search_returns_returnvalue_single() throws Exception {
        MethodElementName testMethod = new MethodElementName(RETURN_FQCN + "#single_method_call_return()");
        MethodElementName targetMethod = new MethodElementName(RETURN_FQCN + "#singleMethodReturn()");
        int targetLine = findReturnLine(targetMethod, "helper(10)");

        long start = System.nanoTime();
        List<SuspiciousExpression> result = searchReturnsReturnValue(testMethod, targetMethod, targetLine, "20");
        long elapsed = System.nanoTime() - start;

        assertFalse(result.isEmpty());
        printBench("SearchReturns/ReturnValue (single)", elapsed);
    }

    @Test
    @Timeout(60)
    void bench_search_returns_returnvalue_multiple() throws Exception {
        MethodElementName testMethod = new MethodElementName(RETURN_FQCN + "#multiple_method_calls_return()");
        MethodElementName targetMethod = new MethodElementName(RETURN_FQCN + "#multipleMethodReturn()");
        int targetLine = findReturnLine(targetMethod, "add(5) + multiply(3)");

        long start = System.nanoTime();
        List<SuspiciousExpression> result = searchReturnsReturnValue(testMethod, targetMethod, targetLine, "19");
        long elapsed = System.nanoTime() - start;

        assertFalse(result.isEmpty());
        printBench("SearchReturns/ReturnValue (multiple)", elapsed);
    }

    // ===== SearchReturns: Argument =====

    @Test
    @Timeout(60)
    void bench_search_returns_argument_single() throws Exception {
        MethodElementName testMethod = new MethodElementName(ARG_FQCN + "#single_method_arg()");
        int targetLine = findAssignLine(testMethod, "result", "target(helper(10))");
        MethodElementName callee = new MethodElementName(ARG_FQCN + "#target(int)");

        long start = System.nanoTime();
        List<SuspiciousExpression> result = searchReturnsArgument(testMethod, targetLine, callee, 0, "20", List.of(1), 2);
        long elapsed = System.nanoTime() - start;

        assertFalse(result.isEmpty());
        printBench("SearchReturns/Argument (single)", elapsed);
    }

    @Test
    @Timeout(60)
    void bench_search_returns_argument_multiple() throws Exception {
        MethodElementName testMethod = new MethodElementName(ARG_FQCN + "#multiple_method_args()");
        int targetLine = findAssignLine(testMethod, "result", "target2(add(5) + multiply(3))");
        MethodElementName callee = new MethodElementName(ARG_FQCN + "#target2(int)");

        long start = System.nanoTime();
        List<SuspiciousExpression> result = searchReturnsArgument(testMethod, targetLine, callee, 0, "19", List.of(1, 2), 3);
        long elapsed = System.nanoTime() - start;

        assertFalse(result.isEmpty());
        printBench("SearchReturns/Argument (multiple)", elapsed);
    }

    // ===== TraceValue: Assignment =====

    @Test
    @Timeout(60)
    void bench_trace_value_assignment() throws Exception {
        MethodElementName testMethod = new MethodElementName(TRACE_ASSIGN_FQCN + "#loop_same_line_multiple_executions()");
        int targetLine = findAssignLine(testMethod, "x", "x + 1");

        long start = System.nanoTime();
        List<TracedValue> result = traceAssignment(testMethod, targetLine, "x", "1");
        long elapsed = System.nanoTime() - start;

        assertFalse(result.isEmpty());
        printBench("TraceValue/Assignment", elapsed);
    }

    // ===== TraceValue: ReturnValue =====

    @Test
    @Timeout(60)
    void bench_trace_value_returnvalue() throws Exception {
        // methodCallReturn() { return doubleValue(y); } で y=21, 戻り値=42
        MethodElementName testMethod = new MethodElementName(TRACE_RETURN_FQCN + "#method_call_return()");
        MethodElementName targetMethod = new MethodElementName(TRACE_RETURN_FQCN + "#methodCallReturn()");
        int targetLine = findReturnLine(targetMethod, "doubleValue(y)");

        long start = System.nanoTime();
        List<TracedValue> result = traceReturnValue(testMethod, targetMethod, targetLine, "42");
        long elapsed = System.nanoTime() - start;

        assertFalse(result.isEmpty());
        printBench("TraceValue/ReturnValue", elapsed);
    }

    // ===== TraceValue: Argument =====

    @Test
    @Timeout(60)
    void bench_trace_value_argument() throws Exception {
        // simple_argument() { helper(x); } で x=10
        MethodElementName testMethod = new MethodElementName(TRACE_ARG_FQCN + "#simple_argument()");
        MethodElementName locateMethod = new MethodElementName(TRACE_ARG_FQCN + "#simple_argument()");
        MethodElementName calleeMethod = new MethodElementName(TRACE_ARG_FQCN + "#helper(int)");
        int targetLine = findMethodCallLine(locateMethod, "helper");

        long start = System.nanoTime();
        List<TracedValue> result = traceArgument(testMethod, locateMethod, calleeMethod, targetLine, "10", 0);
        long elapsed = System.nanoTime() - start;

        assertFalse(result.isEmpty());
        printBench("TraceValue/Argument", elapsed);
    }

    // ===== Helper methods =====

    private static void printBench(String label, long elapsedNanos) {
        logger.info("[BENCH] {} : {} ms", label, elapsedNanos / 1_000_000);
    }

    private static List<SuspiciousExpression> searchReturnsAssignment(
            MethodElementName testMethod, int locateLine, String variableName,
            String actualValue, boolean hasMethodCalling) {
        SuspiciousVariable assignTarget = new SuspiciousLocalVariable(
                testMethod, testMethod.toString(), variableName, actualValue, true, false);
        SuspiciousAssignment suspAssign = new SuspiciousAssignment(
                testMethod, testMethod, locateLine, assignTarget,
                "", hasMethodCalling, List.of(), List.of());
        return new JDISearchSuspiciousReturnsAssignmentStrategy().search(suspAssign);
    }

    private static List<SuspiciousExpression> searchReturnsReturnValue(
            MethodElementName testMethod, MethodElementName targetMethod,
            int locateLine, String actualValue) {
        SuspiciousReturnValue suspReturn = new SuspiciousReturnValue(
                testMethod, targetMethod, locateLine, actualValue,
                "", true, List.of(), List.of());
        return new JDISearchSuspiciousReturnsReturnValueStrategy().search(suspReturn);
    }

    private static List<SuspiciousExpression> searchReturnsArgument(
            MethodElementName testMethod, int locateLine,
            MethodElementName invokeMethodName, int argIndex,
            String actualValue, List<Integer> collectAtCounts, int invokeCallCount) {
        SuspiciousArgument suspArg = new SuspiciousArgument(
                testMethod, testMethod, locateLine, actualValue,
                invokeMethodName, argIndex,
                "", true, List.of(), List.of(),
                collectAtCounts, invokeCallCount);
        return new JDISearchSuspiciousReturnsArgumentStrategy().search(suspArg);
    }

    private static List<TracedValue> traceAssignment(
            MethodElementName testMethod, int locateLine, String variableName, String actualValue) {
        SuspiciousVariable assignTarget = new SuspiciousLocalVariable(
                testMethod, testMethod.toString(), variableName, actualValue, true, false);
        SuspiciousAssignment suspAssign = new SuspiciousAssignment(
                testMethod, testMethod, locateLine, assignTarget, "", false, List.of(), List.of());
        return new JDITraceValueAtSuspiciousAssignmentStrategy().traceAllValuesAtSuspExpr(suspAssign);
    }

    private static List<TracedValue> traceReturnValue(
            MethodElementName testMethod, MethodElementName targetMethod,
            int locateLine, String actualValue) {
        SuspiciousReturnValue suspReturn = new SuspiciousReturnValue(
                testMethod, targetMethod, locateLine, actualValue,
                "", false, List.of(), List.of());
        return new JDITraceValueAtSuspiciousReturnValueStrategy().traceAllValuesAtSuspExpr(suspReturn);
    }

    private static List<TracedValue> traceArgument(
            MethodElementName testMethod, MethodElementName locateMethod,
            MethodElementName calleeMethod, int locateLine, String actualValue, int argIndex) {
        SuspiciousArgument suspArg = new SuspiciousArgument(
                testMethod, locateMethod, locateLine, actualValue,
                calleeMethod, argIndex,
                "", false, List.of(), List.of(), List.of(), 0);
        return new JDITraceValueAtSuspiciousArgumentStrategy().traceAllValuesAtSuspExpr(suspArg);
    }

    private static int findAssignLine(MethodElementName method, String var, String rhsLiteral)
            throws NoSuchFileException {
        BlockStmt bs = JavaParserUtils.extractBodyOfMethod(method);
        assertNotNull(bs, "method body is null: " + method);
        var found = bs.findAll(AssignExpr.class).stream()
                .filter(ae -> targetNameOf(ae.getTarget()).equals(var))
                .filter(ae -> ae.getValue().toString().equals(rhsLiteral))
                .findFirst();
        assertTrue(found.isPresent(), "代入行が見つかりません: " + var + " = " + rhsLiteral + " in " + method);
        return found.get().getBegin().orElseThrow().line;
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

    private static int findMethodCallLine(MethodElementName method, String methodName) throws NoSuchFileException {
        BlockStmt bs = JavaParserUtils.extractBodyOfMethod(method);
        assertNotNull(bs, "method body is null: " + method);
        var found = bs.findAll(MethodCallExpr.class).stream()
                .filter(mce -> mce.getNameAsString().equals(methodName))
                .findFirst();
        assertTrue(found.isPresent(), "メソッド呼び出しが見つかりません: " + methodName + " in " + method);
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
}