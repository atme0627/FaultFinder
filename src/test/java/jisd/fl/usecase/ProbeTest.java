package jisd.fl.usecase;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.*;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.infra.javaparser.JavaParserUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Probe の統合テスト。
 *
 * 期待される木構造を定義し、実際の結果と比較することで厳密に検証する。
 * - 行番号は fixture の AST から導出（ハードコードしない）
 * - JDI は並列実行しない
 */
@Execution(ExecutionMode.SAME_THREAD)
class ProbeTest {

    private static final String FIXTURE_FQCN = "jisd.fixture.ProbeFixture";
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

    @AfterAll
    static void restoreProjectConfig() {
        if (original != null) {
            PropertyLoader.setProjectConfig(original);
        }
    }

    // =====================================================================
    // シナリオ 1: 単純な代入追跡
    // =====================================================================

    @Test
    @Timeout(30)
    void scenario1_simple_assignment() throws Exception {
        // int x = 1; の行がルートノード（actual 値は "1"）
        // 期待: ASSIGN(x=1) のみ、子ノードなし
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#scenario1_simple_assignment()");
        int declLine = findLocalDeclLine(m, "x");

        CauseTreeNode actual = runProbe(m, "x", "1");

        ExpectedNode expected = assign(declLine);
        assertTreeEquals(expected, actual);
    }

    @Test
    @Timeout(30)
    void scenario1_assignment_with_neighbors() throws Exception {
        // int result = a + b; は隣接変数 a, b を持つ
        // 期待: ASSIGN(result=30)
        //         ├── ASSIGN(a=10)
        //         └── ASSIGN(b=20)
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#scenario1_assignment_with_neighbors()");
        int declLineResult = findLocalDeclLine(m, "result");
        int declLineA = findLocalDeclLine(m, "a");
        int declLineB = findLocalDeclLine(m, "b");

        CauseTreeNode actual = runProbe(m, "result", "30");

        ExpectedNode expected = assign(declLineResult,
                assign(declLineA),
                assign(declLineB)
        );
        assertTreeEquals(expected, actual);
    }

    // =====================================================================
    // シナリオ 2: メソッド戻り値追跡
    // =====================================================================

    @Test
    @Timeout(30)
    void scenario2_single_method_return() throws Exception {
        // int x = helper(10); は helper メソッドの return を子ノードに持つ
        // return n * 2 の n は引数由来なので、呼び出し元の引数式を追跡
        // 期待: ASSIGN(x=20)
        //         └── RETURN(helper: return n * 2)
        //               └── ARGUMENT(helper(10) の引数 10)
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#scenario2_single_method_return()");
        MethodElementName helperMethod = new MethodElementName(FIXTURE_FQCN + "#helper(int)");
        int declLine = findLocalDeclLine(m, "x");
        int returnLine = findReturnLine(helperMethod);

        CauseTreeNode actual = runProbe(m, "x", "20");

        // return n * 2 の n は引数由来 → 呼び出し元の引数式（リテラル 10）を追跡
        // 引数がリテラルなので、ARGUMENT の子ノードは空
        ExpectedNode expected = assign(declLine,
                ret(returnLine,
                        arg(declLine)  // helper(10) の引数は同じ行にある
                )
        );
        assertTreeEquals(expected, actual);
    }

    @Test
    @Timeout(30)
    void scenario2_method_with_variable_args() throws Exception {
        // int x = calc(a, b); は calc の return を追跡し、
        // return a + b の a, b は引数由来なので、呼び出し元の引数式を追跡
        // 期待: ASSIGN(x=30)
        //         └── RETURN(calc: return a + b)
        //               ├── ARGUMENT(calc(a,b) の a) → ASSIGN(a=10)
        //               └── ARGUMENT(calc(a,b) の b) → ASSIGN(b=20)
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#scenario2_method_with_variable_args()");
        MethodElementName calcMethod = new MethodElementName(FIXTURE_FQCN + "#calc(int, int)");
        int declLineX = findLocalDeclLine(m, "x");
        int declLineA = findLocalDeclLine(m, "a");
        int declLineB = findLocalDeclLine(m, "b");
        int returnLine = findReturnLine(calcMethod);

        CauseTreeNode actual = runProbe(m, "x", "30");

        // return a + b の a, b は引数由来 → 呼び出し元の引数式（変数 a, b）を追跡
        // 引数が変数なので、その代入元を追跡
        ExpectedNode expected = assign(declLineX,
                ret(returnLine,
                        arg(declLineX,  // calc(a, b) の引数 a（同じ行）
                                assign(declLineA)
                        ),
                        arg(declLineX,  // calc(a, b) の引数 b（同じ行）
                                assign(declLineB)
                        )
                )
        );
        assertTreeEquals(expected, actual);
    }

    // =====================================================================
    // シナリオ 3: ネスト追跡
    // =====================================================================

    @Test
    @Timeout(30)
    void scenario3_nested_method_calls() throws Exception {
        // int x = outer(inner(5)); は最外の outer のみを直接追跡
        // inner は outer の引数として ARGUMENT 追跡経由で発見される
        // 期待: ASSIGN(x=30)
        //         └── RETURN(outer: return n * 3)
        //               └── ARGUMENT(outer(inner(5)) の引数 inner(5))
        //                     └── RETURN(inner: return n * 2)
        //                           └── ARGUMENT(inner(5) の引数 5) (leaf)
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#scenario3_nested_method_calls()");
        MethodElementName innerMethod = new MethodElementName(FIXTURE_FQCN + "#inner(int)");
        MethodElementName outerMethod = new MethodElementName(FIXTURE_FQCN + "#outer(int)");
        int declLine = findLocalDeclLine(m, "x");
        int innerReturnLine = findReturnLine(innerMethod);
        int outerReturnLine = findReturnLine(outerMethod);

        CauseTreeNode actual = runProbe(m, "x", "30");

        ExpectedNode expected = assign(declLine,
                ret(outerReturnLine,
                        arg(declLine,
                                ret(innerReturnLine,
                                        arg(declLine)  // inner(5) の引数 5（リテラルなので leaf）
                                )
                        )
                )
        );
        assertTreeEquals(expected, actual);
    }

    @Test
    @Timeout(30)
    void scenario3_multi_level_nesting() throws Exception {
        // int result = process(input); は process -> transform の連鎖を追跡
        // process の return が transform を呼び出し、transform の引数 n は process の引数 n 由来、
        // process の引数 n は呼び出し元の input 由来という連鎖
        // 期待: ASSIGN(result=11)
        //         └── RETURN(process: return transform(n))
        //               └── RETURN(transform: return n + 1)
        //                     └── ARGUMENT(transform(n) の n) @ process
        //                           └── ARGUMENT(process(input) の input)
        //                                 └── ASSIGN(input=10)
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#scenario3_multi_level_nesting()");
        MethodElementName processMethod = new MethodElementName(FIXTURE_FQCN + "#process(int)");
        MethodElementName transformMethod = new MethodElementName(FIXTURE_FQCN + "#transform(int)");
        int declLineResult = findLocalDeclLine(m, "result");
        int declLineInput = findLocalDeclLine(m, "input");
        int processReturnLine = findReturnLine(processMethod);
        int transformReturnLine = findReturnLine(transformMethod);

        CauseTreeNode actual = runProbe(m, "result", "11");

        ExpectedNode expected = assign(declLineResult,
                ret(processReturnLine,
                        ret(transformReturnLine,
                                arg(processReturnLine,  // transform(n) の引数 n
                                        arg(declLineResult,  // process(input) の引数 input
                                                assign(declLineInput)
                                        )
                                )
                        )
                )
        );
        assertTreeEquals(expected, actual);
    }

    // =====================================================================
    // シナリオ 4: ループ内追跡
    // =====================================================================

    @Test
    @Timeout(30)
    void scenario4_loop_variable_update() throws Exception {
        // for 内の x = x + i; の行が追跡される
        // x = 0, i=0: x=0, i=1: x=1, i=2: x=3 -> 最終値は 3
        // 期待: ASSIGN(x=x+i at loop, final value 3)
        //         ├── ASSIGN(x の前の値を追跡)
        //         └── (i はループ変数なので追跡対象外の可能性)
        //
        // 注: ループの場合、x = x + i の右辺の x は同じ行を指すため、
        //     無限ループを防ぐために探索済み変数はスキップされる。
        //     よって子ノードは空になる可能性がある。
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#scenario4_loop_variable_update()");
        int loopAssignLine = findAssignLine(m, "x", "x + i");

        CauseTreeNode actual = runProbe(m, "x", "3");

        // ルートノードがループ内の代入行であることを確認
        assertNotNull(actual.expression(), "ルートノードの式は null であってはならない");
        assertEquals(loopAssignLine, actual.expression().locateLine(),
                "ルートノードの行番号がループ内の代入行と一致するべき");
        assertInstanceOf(SuspiciousAssignment.class, actual.expression(),
                "ルートノードは SuspiciousAssignment であるべき");
    }

    @Test
    @Timeout(30)
    void scenario4_loop_with_method_call() throws Exception {
        // x = compute(i); は compute の return を追跡
        // return n * 2 の n は引数由来なので、呼び出し元の引数 i を追跡
        // ただし i はループ変数で原因行を特定できないため leaf
        // 期待: ASSIGN(x=4)
        //         └── RETURN(compute: return n * 2)
        //               └── ARGUMENT(compute(i) の引数 i) (leaf)
        MethodElementName m = new MethodElementName(FIXTURE_FQCN + "#scenario4_loop_with_method_call()");
        MethodElementName computeMethod = new MethodElementName(FIXTURE_FQCN + "#compute(int)");
        int loopAssignLine = findAssignLine(m, "x", "compute(i)");
        int returnLine = findReturnLine(computeMethod);

        CauseTreeNode actual = runProbe(m, "x", "4");

        ExpectedNode expected = assign(loopAssignLine,
                ret(returnLine,
                        arg(loopAssignLine)  // compute(i) の引数 i（ループ変数なので leaf）
                )
        );
        assertTreeEquals(expected, actual);
    }

    // =====================================================================
    // 期待される木構造を表現するためのヘルパークラス
    // =====================================================================

    /**
     * 期待されるノードを表現する
     */
    private record ExpectedNode(
            int line,
            Class<? extends SuspiciousExpression> type,
            List<ExpectedNode> children
    ) {
        ExpectedNode(int line, Class<? extends SuspiciousExpression> type, ExpectedNode... children) {
            this(line, type, List.of(children));
        }
    }

    private static ExpectedNode assign(int line, ExpectedNode... children) {
        return new ExpectedNode(line, SuspiciousAssignment.class, children);
    }

    private static ExpectedNode ret(int line, ExpectedNode... children) {
        return new ExpectedNode(line, SuspiciousReturnValue.class, children);
    }

    private static ExpectedNode arg(int line, ExpectedNode... children) {
        return new ExpectedNode(line, SuspiciousArgument.class, children);
    }

    // =====================================================================
    // 木構造の比較
    // =====================================================================

    /**
     * 期待される木構造と実際の木構造を比較する
     */
    private static void assertTreeEquals(ExpectedNode expected, CauseTreeNode actual) {
        assertTreeEqualsRecursive(expected, actual, "root");
    }

    private static void assertTreeEqualsRecursive(ExpectedNode expected, CauseTreeNode actual, String path) {
        // ノード自体の検証
        assertNotNull(actual.expression(), path + ": suspExpr が null");
        assertEquals(expected.line(), actual.expression().locateLine(),
                path + ": 行番号が一致しない");
        assertInstanceOf(expected.type(), actual.expression(),
                path + ": 式の型が一致しない (expected: " + expected.type().getSimpleName() +
                        ", actual: " + actual.expression().getClass().getSimpleName() + ")");

        // 子ノードの数を検証
        assertEquals(expected.children().size(), actual.children().size(),
                path + ": 子ノードの数が一致しない\n" +
                        "  expected children lines: " + expected.children().stream().map(ExpectedNode::line).toList() + "\n" +
                        "  actual children lines: " + actual.children().stream()
                        .map(n -> n.expression() != null ? n.expression().locateLine() : -1).toList());

        // 子ノードを行番号でマッチングして再帰的に検証
        List<ExpectedNode> expectedChildren = new ArrayList<>(expected.children());
        List<CauseTreeNode> actualChildren = new ArrayList<>(actual.children());

        for (ExpectedNode expectedChild : expectedChildren) {
            CauseTreeNode matchingActual = findMatchingChild(expectedChild, actualChildren);
            assertNotNull(matchingActual,
                    path + ": 期待される子ノード (line=" + expectedChild.line() +
                            ", type=" + expectedChild.type().getSimpleName() + ") が見つからない\n" +
                            "  actual children: " + actualChildren.stream()
                            .map(n -> "line=" + (n.expression() != null ? n.expression().locateLine() : -1) +
                                    ", type=" + (n.expression() != null ? n.expression().getClass().getSimpleName() : "null"))
                            .toList());

            actualChildren.remove(matchingActual);
            String childPath = path + " -> " + expectedChild.type().getSimpleName() + "@" + expectedChild.line();
            assertTreeEqualsRecursive(expectedChild, matchingActual, childPath);
        }
    }

    /**
     * 期待されるノードに一致する実際の子ノードを探す。
     * 同じ行番号・型のノードが複数ある場合は、子ノードの構造も考慮してマッチングする。
     */
    private static CauseTreeNode findMatchingChild(ExpectedNode expected, List<CauseTreeNode> actualChildren) {
        List<CauseTreeNode> candidates = actualChildren.stream()
                .filter(actual -> actual.expression() != null)
                .filter(actual -> actual.expression().locateLine() == expected.line())
                .filter(actual -> expected.type().isInstance(actual.expression()))
                .toList();

        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.getFirst();
        }

        // 同じ行番号・型のノードが複数ある場合、子ノードの構造でマッチング
        for (CauseTreeNode candidate : candidates) {
            if (matchesStructure(expected, candidate)) {
                return candidate;
            }
        }
        // 構造マッチが見つからない場合は最初の候補を返す
        return candidates.getFirst();
    }

    /**
     * 期待されるノードと実際のノードの構造が一致するか確認する
     */
    private static boolean matchesStructure(ExpectedNode expected, CauseTreeNode actual) {
        if (expected.children().size() != actual.children().size()) {
            return false;
        }
        // 子ノードの行番号セットが一致するか確認
        List<Integer> expectedChildLines = expected.children().stream()
                .map(ExpectedNode::line)
                .sorted()
                .toList();
        List<Integer> actualChildLines = actual.children().stream()
                .filter(n -> n.expression() != null)
                .map(n -> n.expression().locateLine())
                .sorted()
                .toList();
        return expectedChildLines.equals(actualChildLines);
    }

    // =====================================================================
    // ヘルパーメソッド
    // =====================================================================

    private static CauseTreeNode runProbe(MethodElementName testMethod, String variableName, String actualValue) {
        SuspiciousLocalVariable target = new SuspiciousLocalVariable(
                testMethod, testMethod, variableName, actualValue, true);
        Probe probe = new Probe(target);
        return probe.run(0);
    }

    private static int findLocalDeclLine(MethodElementName method, String var) {
        List<Integer> lines = JavaParserUtils.findLocalVariableDeclarationLine(method, var);
        assertFalse(lines.isEmpty(), "宣言行が見つからない: " + method + " var=" + var);
        return lines.get(0);
    }

    private static int findReturnLine(MethodElementName method) throws NoSuchFileException {
        BlockStmt bs = JavaParserUtils.extractBodyOfMethod(method);
        assertNotNull(bs, "method body is null: " + method);

        return bs.findAll(ReturnStmt.class).stream()
                .filter(rs -> rs.getBegin().isPresent())
                .findFirst()
                .map(rs -> rs.getBegin().get().line)
                .orElseThrow(() -> new AssertionError("return 文が見つからない: " + method));
    }

    private static int findAssignLine(MethodElementName method, String var, String rhsLiteral) throws NoSuchFileException {
        BlockStmt bs = JavaParserUtils.extractBodyOfMethod(method);
        assertNotNull(bs, "method body is null: " + method);

        return bs.findAll(AssignExpr.class).stream()
                .filter(ae -> ae.getTarget().toString().equals(var))
                .filter(ae -> ae.getValue().toString().equals(rhsLiteral))
                .findFirst()
                .map(ae -> ae.getBegin().orElseThrow().line)
                .orElseThrow(() -> new AssertionError("代入行が見つからない: " + var + " = " + rhsLiteral + " in " + method));
    }
}
