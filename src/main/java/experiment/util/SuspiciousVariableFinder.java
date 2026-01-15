package experiment.util;

import com.github.javaparser.ast.stmt.Statement;
import experiment.util.internal.finder.LineMethodCallWatcher;
import experiment.util.internal.finder.LineVariableNameExtractor;
import experiment.util.internal.finder.TestLauncherForFinder;
import experiment.util.internal.finder.LineValueWatcher;
import jisd.fl.core.domain.NeighborSuspiciousVariablesSearcher;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.infra.javaparser.JavaParserUtils;

import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 失敗テストに対して、probeの対象とする変数を自動で抽出するクラス
 * assertion fail または crash が起きた行に含まれるprimitive変数、
 * 及びそこで呼び出されているメソッドの返り値に含まれるprimitive変数を全てSuspiciousVariableとして抽出する。
 */
public class SuspiciousVariableFinder {
    private final MethodElementName targetTestCaseName;

    private final TestLauncherForFinder testLauncher;
    private final LineVariableNameExtractor VarNameExtractor;
    private final LineValueWatcher valueWatcher;
    private final LineMethodCallWatcher methodCallWatcher;
    private final NeighborSuspiciousVariablesSearcher neighborSearcher;

    public SuspiciousVariableFinder(MethodElementName targetTestCaseName) throws NoSuchFileException {
        this.targetTestCaseName = targetTestCaseName;

        this.testLauncher = new TestLauncherForFinder(targetTestCaseName);
        this.VarNameExtractor = new LineVariableNameExtractor();
        this.valueWatcher = new LineValueWatcher(targetTestCaseName);
        this.methodCallWatcher = new LineMethodCallWatcher(targetTestCaseName);
        this.neighborSearcher = new NeighborSuspiciousVariablesSearcher();
    }


    public List<SuspiciousVariable> findSuspiciousVariableInAssertLine() throws NoSuchFileException {
        //失敗テストを実行し、失敗したAssert行、またはクラッシュ時に最後に実行された行（失敗行）を取得
        TestLauncherForFinder.TestFailureInfo info = testLauncher.runTestAndGetFailureLine().orElse(null);
        if(info == null) return Collections.emptyList();

        //失敗行のロケーション情報を取得
        int failureLine = info.line();
        MethodElementName locateClass = info.locateClass();
        Map<Integer, MethodElementName> result1 = JavaParserUtils.getMethodNamesWithLine(locateClass);
        MethodElementName locateMethod = result1.get(failureLine);

        //ログ
        System.out.println("****** failure test: " + targetTestCaseName + "   location:  " + locateMethod + " ( line: "+ failureLine +" ) " + "*****************");

        //失敗行に含まれる変数名をStringとして静的解析にて取得する。
        //assert文の場合、まずAssert文で使われてる変数全て取ってくる(actualかどうか考えない)
        Statement stmtInFailureLine = VarNameExtractor.extractStmtInFailureLine(failureLine, locateMethod);
        List<String> neighborVariableNames = VarNameExtractor.extractVariableNamesInLine(failureLine, locateMethod);

        //失敗行に含まれる各変数の、テスト実行時の値を動的解析で取得する。
        List<SuspiciousVariable> result = new ArrayList<>();
        result.addAll(valueWatcher.watchAllValuesInAssertLine(failureLine, locateMethod));
        result = result.stream().filter(sv -> neighborVariableNames.contains(sv.getSimpleVariableName()))
                .collect(Collectors.toList());

        //失敗行で呼び出しが行われているメソッドの情報を動的解析で取得し、それらのreturn行で使用されている変数の情報を抽出する。
        //TODO: treeNodeをなんとかする
        List<SuspiciousExpression> returns = methodCallWatcher.searchSuspiciousReturns(failureLine, locateMethod);
        for (SuspiciousExpression r : returns) {
            //SuspExprで観測できる全ての変数
            List<SuspiciousVariable> neighbor = neighborSearcher.neighborSuspiciousVariables(false, r);
            result.addAll(neighbor);

        }

        return result;
    }
}
