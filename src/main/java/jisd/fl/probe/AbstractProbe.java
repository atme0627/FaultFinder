package jisd.fl.probe;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.sun.jdi.*;
import jisd.debug.*;
import jisd.debug.Location;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.info.*;
import jisd.fl.probe.record.TracedValue;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.probe.record.TracedValuesOfTarget;
import jisd.fl.util.QuietStdOut;
import jisd.fl.util.analyze.*;
import jisd.fl.util.TestUtil;

import java.nio.file.NoSuchFileException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractProbe {

    SuspiciousVariable firstTarget;
    MethodElementName failedTest;
    Debugger dbg;

    public AbstractProbe(SuspiciousVariable target) {
        this.firstTarget = target;
        this.failedTest = firstTarget.getFailedTest();
        this.dbg = createDebugger();
    }

    /**
     * 与えられたSuspiciousVariableに対して、その直接的な原因となるExpressionをSuspiciousExpressionとして返す
     * 原因が呼び出し元の引数にある場合は、その引数のExprに対応するものを返す
     * @param sleepTime
     * @param suspVar
     * @return
     */
    protected Optional<SuspiciousExpression> probing(int sleepTime, SuspiciousVariable suspVar){
        //ターゲット変数が変更されうる行を観測し、全変数の情報を取得
        System.out.println("    >> Probe Info: Running debugger and extract watched info.");
        TracedValueCollection tracedValues = traceValuesOfTarget(suspVar, sleepTime);

        tracedValues.printAll();
        //対象の変数に変更が起き、actualを取るようになった行（原因行）を探索
        List<TracedValue> watchedValues = tracedValues.getAll();

        System.out.println("    >> Probe Info: Searching probe line.");
        Optional<SuspiciousExpression> result = searchProbeLine(watchedValues, suspVar);
        return result;
    }

    //variableInfoに指定された変数のみを観測し、各行で取っている値を記録する
    protected TracedValueCollection traceValuesOfTarget(SuspiciousVariable target, int sleepTime){
        //disable output
        try (QuietStdOut q = QuietStdOut.suppress()) {
            List<Integer> canSetLines = StaticAnalyzer.getCanSetLine(target);
            String dbgMain = target.getLocateClass();
            dbg = createDebugger();
            String[] targetValueName = new String[]{(target.isField() ? "this." : "") + target.getSimpleVariableName()};
            //set watchPoint
            dbg.setMain(dbgMain);
            List<Optional<Point>> watchPoints =
                    canSetLines.stream()
                            .map(l -> dbg.watch(l, targetValueName))
                            .collect(Collectors.toList());

            //run Test debugger
            try {
                dbg.run(sleepTime);
            } catch (VMDisconnectedException | InvalidStackFrameException e) {
                //throw new RuntimeException(e);
                System.err.println(e);
            } catch (NullPointerException ignored) {
            }

            //各行でのデバッグ情報
            List<DebugResult> drs = watchPoints.stream()
                    //WatchPointからDebugResultを得る
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(wp -> wp.getResults(targetValueName[0]))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());

            //各行での値の情報 (Location情報なし)
            List<ValueInfo> valuesOfTarget = drs.stream()
                    .map(DebugResult::getValues)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());

            //LocalDateTime --> Locationのマップ
            Map<LocalDateTime, Location> locationAtTime = new HashMap<>();
            for (DebugResult dr : drs) {
                Location loc = dr.getLocation();
                for (ValueInfo vi : dr.getValues()) {
                    locationAtTime.put(vi.getCreatedAt(), loc);
                }
            }

            TracedValueCollection watchedValues = new TracedValuesOfTarget(target, valuesOfTarget, locationAtTime);
            dbg.exit();
            dbg.clearResults();
            return watchedValues;
        }
    }

    /**
     *
     *  1. 代入によって変数がactualの値を取るようになったパターン
     *      1a. すでに定義されていた変数に代入が行われたパターン
     *      1b. 宣言と同時に行われた初期化によってactualの値を取るパターン
     *  2. その変数が引数由来で、かつメソッド内で上書きされていないパターン
     *  3. throw内などブレークポイントが置けない行で、代入が行われているパターン --> 未想定
     * @param tracedValues
     * @param vi
     * @return
     */
    private Optional<SuspiciousExpression> searchProbeLine(List<TracedValue> tracedValues, SuspiciousVariable vi){
        try (QuietStdOut q = QuietStdOut.suppress()) {
            //対象の変数に値の変化が起きている行の特定
            List<Integer> valueChangingLines = valueChangingLine(vi);

            /* 1a. すでに定義されていた変数に代入が行われたパターン */
            //代入の実行後にactualの値に変化している行の特定(ない場合あり)
            List<TracedValue> changeToActualLines = valueChangedToActualLine(tracedValues, valueChangingLines, vi.getActualValue());
            //代入の実行後にactualの値に変化している行あり -> その中で最後に実行された行がprobe line
            if (!changeToActualLines.isEmpty()) {
                //原因行
                TracedValue causeLine = changeToActualLines.get(changeToActualLines.size() - 1);
                int causeLineNumber = causeLine.lineNumber;
                return Optional.of(resultIfAssigned(causeLineNumber, vi));
            }

            //fieldは代入以外での値の変更を特定できない
            if (vi.isField()) {
                System.err.println("Cannot find probe line of field. [FIELD NAME] " + vi.getSimpleVariableName());
                return Optional.empty();
            }

            /* 1b. 宣言と同時に行われた初期化によってactualの値を取るパターン */
            //初期化の時点でその値が代入されている
            //変数が存在し、宣言と同時に初期化がされている時点で、これを満たすことにする

            //実行しているメソッドを取得
            MethodElement locateMethodElement;
            try {
                locateMethodElement = MethodElement.getMethodElementByName(vi.getLocateMethodElement());
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }

            //targetVariableのVariableDeclaratorを特定
            Optional<VariableDeclarator> ovd = locateMethodElement.findLocalVarDeclaration(vi.getSimpleVariableName());
            boolean isThereVariableDeclaration = ovd.isPresent() && ovd.get().getInitializer().isPresent();
            if (isThereVariableDeclaration) {
                int varDeclarationLine = ovd.get().getBegin().get().line;
                return Optional.of(resultIfAssigned(varDeclarationLine, vi));
            }

            /* 2. その変数が引数由来で、かつメソッド内で上書きされていないパターン */
            //初めて変数が観測された時点ですでにactualの値を取っている
            TracedValue firstMatchedLine = tracedValues.get(0);
            if (vi.getActualValue().equals(firstMatchedLine.value)) {
                return Optional.of(resultIfNotAssigned(vi));
            }

            /* 3. throw内などブレークポイントが置けない行で、代入が行われているパターン */
            System.err.println("There is no value which same to actual.");
            return Optional.empty();
        }
    }

    //TODO: refactor
    private List<Integer> valueChangingLine(SuspiciousVariable vi){
        //代入行の特定
        //unaryExpr(ex a++)も含める
        MethodElementName locateElement = vi.getLocateMethodElement();
        List<Integer> result = new ArrayList<>();
        List<AssignExpr> aes;
        List<UnaryExpr> ues;
        if(vi.isField()) {
            try {
                aes = JavaParserUtil.extractAssignExpr(locateElement);
                CompilationUnit unit = JavaParserUtil.parseClass(locateElement);
                ues = unit.findAll(UnaryExpr.class, (n)-> {
                    UnaryExpr.Operator ope = n.getOperator();
                    return ope == UnaryExpr.Operator.POSTFIX_DECREMENT ||
                            ope == UnaryExpr.Operator.POSTFIX_INCREMENT ||
                            ope == UnaryExpr.Operator.PREFIX_DECREMENT ||
                            ope == UnaryExpr.Operator.PREFIX_INCREMENT;
                });
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            BlockStmt bs = null;
            try {
                bs = JavaParserUtil.extractBodyOfMethod(locateElement);
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }
            aes = bs.findAll(AssignExpr.class);
            ues = bs.findAll(UnaryExpr.class, (n)-> {
                UnaryExpr.Operator ope = n.getOperator();
                return ope == UnaryExpr.Operator.POSTFIX_DECREMENT ||
                        ope == UnaryExpr.Operator.POSTFIX_INCREMENT ||
                        ope == UnaryExpr.Operator.PREFIX_DECREMENT ||
                        ope == UnaryExpr.Operator.PREFIX_INCREMENT;
            });
        }

        for(AssignExpr ae : aes){
            //対象の変数に代入されているか確認
            Expression target = ae.getTarget();
            String targetName;
            if(target.isArrayAccessExpr()) {
                targetName = target.asArrayAccessExpr().getName().toString();
            }
            else if(target.isFieldAccessExpr()){
                targetName = target.asFieldAccessExpr().getName().toString();
            }
            else {
                targetName = target.toString();
            }

            if(targetName.equals(vi.getSimpleVariableName())) {
                if(vi.isField() == target.isFieldAccessExpr())
                    for(int i = ae.getBegin().get().line; i <= ae.getEnd().get().line; i++) {
                        result.add(i);
                    }
            }
        }
        for(UnaryExpr ue : ues){
            //対象の変数に代入されているか確認
            Expression target = ue.getExpression();
            String targetName = target.toString();

            if(targetName.equals(vi.getSimpleVariableName())) {
                if(vi.isField() == target.isFieldAccessExpr())
                    for(int i = ue.getBegin().get().line; i <= ue.getEnd().get().line; i++) {
                        result.add(i);
                    }
            }
        }
        return result;
    }

    private List<TracedValue> valueChangedToActualLine(List<TracedValue> tracedValues, List<Integer> assignedLine, String actual){
        List<TracedValue> changedToActualLines = new ArrayList<>();
        for(int i = 0; i < tracedValues.size() - 1; i++){
            TracedValue watchingLine = tracedValues.get(i);
            //watchingLineでは代入が行われていない -> 原因行ではない
            if(!assignedLine.contains(watchingLine.lineNumber)) continue;
            //次の行で値がactualに変わっている -> その行が原因行の候補
            TracedValue afterAssignLine = tracedValues.get(i+1);
            if(afterAssignLine.value.equals(actual)) changedToActualLines.add(watchingLine);
        }
        changedToActualLines.sort(TracedValue::compareTo);
        return changedToActualLines;
    }


    /** 代入によって変数がactualの値を取るようになったパターン(初期化含む)
     * 値がactualになった行の前に観測した行が、実際に値を変更した行(probe line)
     *  ex.)
     *      SuspClass#suspMethod(){
     *        ...
     *  18: suspVar = a + 10; // <-- suspicious assignment
     *        ...
     *     }
     *
     *     調査対象の変数がfieldの場合もあるので必ずしもsuspicious assignmentは対象の変数と同じメソッドでは起きない
     *     が、同じクラスであることは保証される
     */
    private SuspiciousAssignment resultIfAssigned(int causeLineNumber, SuspiciousVariable vi){
        try {
            //TODO: 毎回静的解析するのは遅すぎるため、キャッシュする方がいい
            Map<Integer, MethodElementName> methodElementNames = StaticAnalyzer.getMethodNamesWithLine(vi.getLocateMethodElement());
            MethodElementName locateMethodElementName = methodElementNames.get(causeLineNumber);
            return new SuspiciousAssignment(vi.getFailedTest(), locateMethodElementName, causeLineNumber, vi);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
    }

    /** 探索対象の変数が現在実行中のメソッドの引数であり、メソッド呼び出しの時点でその値を取っていたパターン
     * メソッドの呼び出し元での対象の変数に対応する引数のExprを特定し、SuspiciousArgumentを取得する
     *
     *  ex.)
     *      CallerClass#callerMethod(){
     *        ...
     *      18: foo = calleeMethod(a + b, c);
     *      //                     ^^^^^
     *      //             suspicious argument
     *        ...
     *     }
     *
     *     CalleeClass#CalleeMethod(x, y){
     *    //                       ^^^
     *    //                 target variable
     *         ...
     *     }
     *
     */
    private SuspiciousArgument resultIfNotAssigned(SuspiciousVariable suspVar){
        //実行しているメソッド名を取得
        MethodElementName locateMethodElementName = suspVar.getLocateMethodElement();
        return SuspiciousArgument.searchSuspiciousArgument(locateMethodElementName, suspVar);
    }


    protected Debugger createDebugger() {
        return createDebugger(failedTest.getFullyQualifiedMethodName());
    }

    protected  Debugger createDebugger(String targetMethod){
        //使い終わったTestLauncherのプロセスが生き残り続ける問題の対策
        if(dbg != null){
            ThreadReference tr = null;
            try {
                tr = dbg.thread();
            } catch(VMDisconnectedException ignored) {
            }

            try {
                if( tr != null && tr.isAtBreakpoint() ){
                    dbg.cont();
                }
            } catch(VMDisconnectedException ignored) {
            }
        dbg.exit();
        }

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return TestUtil.testDebuggerFactory(new MethodElementName(targetMethod));
    }


}