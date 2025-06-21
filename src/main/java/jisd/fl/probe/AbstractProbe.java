package jisd.fl.probe;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.sun.jdi.*;
import jisd.debug.*;
import jisd.debug.Location;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.info.*;
import jisd.fl.probe.record.TracedValue;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.probe.record.TracedValuesAtLine;
import jisd.fl.probe.record.TracedValuesOfTarget;
import jisd.fl.util.analyze.*;
import jisd.fl.util.TestUtil;
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.nio.file.NoSuchFileException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractProbe {

    FailedAssertInfo assertInfo;
    Debugger dbg;
    static PrintStream stdOut = System.out;
    static PrintStream stdErr = System.err;

    public AbstractProbe(FailedAssertInfo assertInfo) {
        this.assertInfo = assertInfo;
        this.dbg = createDebugger();
    }

    //一回のprobeを行う
    //条件を満たす行の情報を返す
    protected ProbeResult probing(int sleepTime, SuspiciousVariable suspiciousVariable){
        //ターゲット変数が変更されうる行を観測し、全変数の情報を取得
        disableStdOut("    >> Probe Info: Running debugger and extract watched info.");
        TracedValueCollection tracedValues = traceValuesOfTarget(suspiciousVariable, sleepTime);
        enableStdOut();
        tracedValues.printAll();
        //対象の変数に変更が起き、actualを取るようになった行（原因行）を探索
        List<TracedValue> watchedValues = tracedValues.filterByVariableName(suspiciousVariable.getVariableName(true, true));
        disableStdOut("    >> Probe Info: Searching probe line.");
        ProbeResult result = searchProbeLine(watchedValues, suspiciousVariable);
        enableStdOut();
        //probe lineが特定できなかった場合nullを返す
        if(result.isNotFound()) return null;
        return result;
    }

    //variableInfoに指定された変数のみを観測し、各行で取っている値を記録する
    protected TracedValueCollection traceValuesOfTarget(SuspiciousVariable target, int sleepTime){
        List<Integer> canSetLines = StaticAnalyzer.getCanSetLine(target);
        String dbgMain = target.getLocateClass();
        dbg = createDebugger();
        String[] targetValueName = new String[]{target.getSimpleVariableName()};
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
        }
        catch (NullPointerException ignored){
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
        for(DebugResult dr : drs){
            Location loc = dr.getLocation();
            for(ValueInfo vi : dr.getValues()){
                locationAtTime.put(vi.getCreatedAt(), loc);
            }
        }

        TracedValueCollection watchedValues = new TracedValuesOfTarget(target, valuesOfTarget, locationAtTime);
        dbg.exit();
        dbg.clearResults();
        return watchedValues;
    }

    //viの原因行で、全ての変数が取っている値を記録する
    //何回目のループで観測された値かを入力する
    //TODO: viと一致するかを調べるシステムにする。
    protected TracedValueCollection traceAllValuesAtLine(CodeElementName targetClassName, int line, int nthLoop, int sleepTime){
        disableStdOut("");
        dbg = createDebugger();
        dbg.setMain(targetClassName.getFullyQualifiedClassName());
        Optional<Point> watchPointAtLine = dbg.watch(line);

        //run Test debugger
        try {
            dbg.run(sleepTime);
        } catch (VMDisconnectedException | InvalidStackFrameException e) {
            System.err.println(e);
        }
        enableStdOut();

        //この行で値が観測されることが保証されている
        List<DebugResult> drs = new ArrayList<>(watchPointAtLine.get().getResults().values());
        Location loc = drs.get(0).getLocation();
        //行のnthLoop番目のvalueInfoを取得
        List<ValueInfo> valuesAtLine = drs.stream()
                .map(DebugResult::getValues)
                .map(vis -> vis.get(nthLoop))
                .collect(Collectors.toList());

        TracedValueCollection watchedValues = new TracedValuesAtLine(valuesAtLine, loc);
        dbg.exit();
        dbg.clearResults();
        return watchedValues;
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
    private ProbeResult searchProbeLine(List<TracedValue> tracedValues, SuspiciousVariable vi){

        //対象の変数に値の変化が起きている行の特定
        List<Integer> valueChangingLines = valueChangingLine(vi);

        /* 1a. すでに定義されていた変数に代入が行われたパターン */
        //代入の実行後にactualの値に変化している行の特定(ない場合あり)
        List<TracedValue> changeToActualLines = valueChangedToActualLine(tracedValues, valueChangingLines, vi.getActualValue());
        //代入の実行後にactualの値に変化している行あり -> その中で最後に実行された行がprobe line
        if(!changeToActualLines.isEmpty()) {
            //原因行
            TracedValue causeLine = changeToActualLines.get(changeToActualLines.size() - 1);
            int causeLineNumber = causeLine.loc.getLineNumber();
            return resultIfAssigned(causeLineNumber, vi);
        }

        //fieldは代入以外での値の変更を特定できない
        if(vi.isField()){
            System.err.println("Cannot find probe line of field. [FIELD NAME] " + vi.getSimpleVariableName());
            return ProbeResult.notFound();
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
            return resultIfAssigned(varDeclarationLine, vi);
        }

        /* 2. その変数が引数由来で、かつメソッド内で上書きされていないパターン */
        //初めて変数が観測された時点ですでにactualの値を取っている
        TracedValue firstMatchedLine = tracedValues.get(0);
        if (vi.getActualValue().equals(firstMatchedLine.value)) {
            return resultIfNotAssigned(vi);
        }

        /* 3. throw内などブレークポイントが置けない行で、代入が行われているパターン */
        System.err.println("There is no value which same to actual.");
        return ProbeResult.notFound();
    }

    //TODO: refactor
    private List<Integer> valueChangingLine(SuspiciousVariable vi){
        //代入行の特定
        //unaryExpr(ex a++)も含める
        CodeElementName locateElement = vi.getLocateMethodElement();
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
            if(!assignedLine.contains(watchingLine.loc.getLineNumber())) continue;
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
    private ProbeResult resultIfAssigned(int causeLineNumber, SuspiciousVariable vi){
        //原因行のStatementを取得
        CodeElementName locateClassElementName = vi.getLocateMethodElement();
        SuspiciousAssignment suspAssignment = new SuspiciousAssignment(vi.getFailedTest(), locateClassElementName, causeLineNumber, vi);
        ProbeResult result = ProbeResult.convertSuspExpr(suspAssignment);
        //原因行で他に登場した値をセット
        TracedValueCollection valuesAtLine = traceAllValuesAtLine(result.probeMethod(), result.probeLine(), result.probeIterateNum(), 2000);
        result.setValuesInLine(valuesAtLine);
        return result;
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
    private ProbeResult resultIfNotAssigned(SuspiciousVariable suspVar){
        //実行しているメソッド名を取得
        CodeElementName locateMethodElementName = suspVar.getLocateMethodElement();

        SuspiciousArgument suspArg = SuspiciousArgument.searchSuspiciousArgument(locateMethodElementName, suspVar);
        ProbeResult result = ProbeResult.convertSuspArg(suspArg);
        return result;
    }

    protected void disableStdOut(String msg){
        enableStdOut();
        if(!msg.isEmpty()) System.out.println(msg);
        PrintStream nop = new PrintStream(new OutputStream() {
            public void write(int b) { /* noop */ }
        });
        System.setOut(nop);
        System.setErr(nop);
    }

    protected void enableStdOut(){
        System.setOut(stdOut);
        System.setErr(stdErr);
    }

    protected void printWatchedValues(TracedValueCollection watchedValues, String variableName){
        //System.out.println("    >> [assigned line] " + Arrays.toString(assignLine.toArray()));
        if(variableName != null) {
            watchedValues.print(variableName);
        }
        else {
            watchedValues.printAll();
        }
    }

    protected void printProbeStatement(ProbeResult result){
        System.out.println("    >> [PROBE LINES]");
        if(result.isCausedByArgument()) {
            System.out.println("    >> Variable defect is derived from caller method. ");
        }
        System.out.println("    >> " + result.getSrc());
    }



    protected Debugger createDebugger() {
        return createDebugger(assertInfo.getTestMethodName());
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
        return TestUtil.testDebuggerFactory(targetMethod);
    }


    public Set<String> getCalleeMethods(CodeElementName targetClass, int line){
        String main = TestUtil.getJVMMain(new CodeElementName(assertInfo.getTestMethodName()));
        String options = TestUtil.getJVMOption();
        EnhancedDebugger edbg = new EnhancedDebugger(main, options);
        //return edbg.getCalleeMethods(targetClass.getFullyQualifiedClassName(), line);
        //TODO: あとで直す
        return Set.of();
    }
}