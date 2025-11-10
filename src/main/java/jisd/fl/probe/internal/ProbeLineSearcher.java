package jisd.fl.probe.internal;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import jisd.fl.probe.info.SuspiciousArgument;
import jisd.fl.probe.info.SuspiciousAssignment;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.probe.record.TracedValue;
import jisd.fl.util.analyze.JavaParserUtil;
import jisd.fl.util.analyze.MethodElementName;
import jisd.fl.util.analyze.StaticAnalyzer;

import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProbeLineSearcher {
    List<TracedValue> tracedValues;
    SuspiciousVariable vi;

    public ProbeLineSearcher(List<TracedValue> tracedValues, SuspiciousVariable vi) {
        this.tracedValues = tracedValues;
        this.vi = vi;
    }

    /**
     * 1. 代入によって変数がactualの値を取るようになったパターン
     * 1a. すでに定義されていた変数に代入が行われたパターン
     * 1b. 宣言と同時に行われた初期化によってactualの値を取るパターン
     * 2. その変数が引数由来で、かつメソッド内で上書きされていないパターン
     * 3. throw内などブレークポイントが置けない行で、代入が行われているパターン --> 未想定
     *
     * @return
     */
    public Optional<SuspiciousExpression> searchProbeLine() {
        //対象の変数に値の変化が起きている行の特定
        List<Integer> valueChangingLines = valueChangingLine();

        //対象の変数を定義している行を追加
        valueChangingLines.addAll(
                //targetVariableのVariableDeclaratorを特定
                StaticAnalyzer.findLocalVarDeclaration(vi.getLocateMethodElement(), vi.getSimpleVariableName())
                        .stream()
                        .map(vd -> vd.getRange().get().begin.line)
                        .toList()
        );

        /* 1a. すでに定義されていた変数に代入が行われたパターン */
        //代入の実行後にactualの値に変化している行の特定(ない場合あり)
        List<TracedValue> changeToActualLines = valueChangedToActualLine(valueChangingLines, vi.getActualValue());
        //代入の実行後にactualの値に変化している行あり -> その中で最後に実行された行がprobe line
        if (!changeToActualLines.isEmpty()) {
            //原因行
            TracedValue causeLine = changeToActualLines.get(changeToActualLines.size() - 1);
            int causeLineNumber = causeLine.lineNumber;
            return Optional.of(resultIfAssigned(causeLineNumber));
        }

        //fieldは代入以外での値の変更を特定できない
        if (vi.isField()) {
            System.err.println("Cannot find probe line of field. [FIELD NAME] " + vi.getSimpleVariableName());
            return Optional.empty();
        }

        /* 2. その変数が引数由来で、かつメソッド内で上書きされていないパターン */
        //初めて変数が観測された時点ですでにactualの値を取っている
        return resultIfNotAssigned();

        /* 3. throw内などブレークポイントが置けない行で、代入が行われているパターン */
//            System.err.println("There is no value which same to actual.");
//            return Optional.empty();
    }

    //TODO: refactor
    private List<Integer> valueChangingLine() {
        //代入行の特定
        //unaryExpr(ex a++)も含める
        MethodElementName locateElement = vi.getLocateMethodElement();
        List<Integer> result = new ArrayList<>();
        List<AssignExpr> aes;
        List<UnaryExpr> ues;
        if (vi.isField()) {
            try {
                aes = JavaParserUtil.extractAssignExpr(locateElement);
                CompilationUnit unit = JavaParserUtil.parseClass(locateElement);
                ues = unit.findAll(UnaryExpr.class, (n) -> {
                    UnaryExpr.Operator ope = n.getOperator();
                    return ope == UnaryExpr.Operator.POSTFIX_DECREMENT ||
                            ope == UnaryExpr.Operator.POSTFIX_INCREMENT ||
                            ope == UnaryExpr.Operator.PREFIX_DECREMENT ||
                            ope == UnaryExpr.Operator.PREFIX_INCREMENT;
                });
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }
        } else {
            BlockStmt bs = null;
            try {
                bs = JavaParserUtil.searchBodyOfMethod(locateElement);
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }
            aes = bs.findAll(AssignExpr.class);
            ues = bs.findAll(UnaryExpr.class, (n) -> {
                UnaryExpr.Operator ope = n.getOperator();
                return ope == UnaryExpr.Operator.POSTFIX_DECREMENT ||
                        ope == UnaryExpr.Operator.POSTFIX_INCREMENT ||
                        ope == UnaryExpr.Operator.PREFIX_DECREMENT ||
                        ope == UnaryExpr.Operator.PREFIX_INCREMENT;
            });
        }

        for (AssignExpr ae : aes) {
            //対象の変数に代入されているか確認
            Expression target = ae.getTarget();
            String targetName;
            if (target.isArrayAccessExpr()) {
                targetName = target.asArrayAccessExpr().getName().toString();
            } else if (target.isFieldAccessExpr()) {
                targetName = target.asFieldAccessExpr().getName().toString();
            } else {
                targetName = target.toString();
            }

            if (targetName.equals(vi.getSimpleVariableName())) {
                if (vi.isField() == target.isFieldAccessExpr())
                    for (int i = ae.getBegin().get().line; i <= ae.getEnd().get().line; i++) {
                        result.add(i);
                    }
            }
        }
        for (UnaryExpr ue : ues) {
            //対象の変数に代入されているか確認
            Expression target = ue.getExpression();
            String targetName = target.toString();

            if (targetName.equals(vi.getSimpleVariableName())) {
                if (vi.isField() == target.isFieldAccessExpr())
                    for (int i = ue.getBegin().get().line; i <= ue.getEnd().get().line; i++) {
                        result.add(i);
                    }
            }
        }
        return result;
    }

    private List<TracedValue> valueChangedToActualLine(List<Integer> assignedLine, String actual) {
        List<TracedValue> changedToActualLines = new ArrayList<>();
        for (int i = 0; i < tracedValues.size() - 1; i++) {
            TracedValue watchingLine = tracedValues.get(i);
            //watchingLineでは代入が行われていない -> 原因行ではない
            if (!assignedLine.contains(watchingLine.lineNumber)) continue;
            //次の行で値がactualに変わっている -> その行が原因行の候補
            TracedValue afterAssignLine = tracedValues.get(i + 1);
            if (afterAssignLine.value.equals(actual)) changedToActualLines.add(watchingLine);
        }
        changedToActualLines.sort(TracedValue::compareTo);
        return changedToActualLines;
    }


    /**
     * 代入によって変数がactualの値を取るようになったパターン(初期化含む)
     * 値がactualになった行の前に観測した行が、実際に値を変更した行(probe line)
     * ex.)
     * SuspClass#suspMethod(){
     * ...
     * 18: suspVar = a + 10; // <-- suspicious assignment
     * ...
     * }
     * <p>
     * 調査対象の変数がfieldの場合もあるので必ずしもsuspicious assignmentは対象の変数と同じメソッドでは起きない
     * が、同じクラスであることは保証される
     */
    private SuspiciousAssignment resultIfAssigned(int causeLineNumber) {
        try {
            //TODO: 毎回静的解析するのは遅すぎるため、キャッシュする方がいい
            Map<Integer, MethodElementName> methodElementNames = StaticAnalyzer.getMethodNamesWithLine(vi.getLocateMethodElement());
            MethodElementName locateMethodElementName = methodElementNames.get(causeLineNumber);
            return new SuspiciousAssignment(vi.getFailedTest(), locateMethodElementName, causeLineNumber, vi);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 探索対象の変数が現在実行中のメソッドの引数であり、メソッド呼び出しの時点でその値を取っていたパターン
     * メソッドの呼び出し元での対象の変数に対応する引数のExprを特定し、SuspiciousArgumentを取得する
     * <p>
     * ex.)
     * CallerClass#callerMethod(){
     * ...
     * 18: foo = calleeMethod(a + b, c);
     * //                     ^^^^^
     * //             suspicious argument
     * ...
     * }
     * <p>
     * CalleeClass#CalleeMethod(x, y){
     * //                       ^^^
     * //                 target variable
     * ...
     * }
     */
    private Optional<SuspiciousExpression> resultIfNotAssigned() {
        //実行しているメソッド名を取得
        MethodElementName locateMethodElementName = vi.getLocateMethodElement();
        Optional<SuspiciousArgument> result = SuspiciousArgument.searchSuspiciousArgument(locateMethodElementName, vi);
        if(result.isEmpty()){
            return Optional.empty();
        }
        else {
            return Optional.of(result.get());
        }
    }

}
