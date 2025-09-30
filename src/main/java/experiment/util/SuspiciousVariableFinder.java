package experiment.util;

import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import jisd.debug.*;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousReturnValue;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.*;

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

    public SuspiciousVariableFinder(MethodElementName targetTestCaseName) throws NoSuchFileException {
        this.targetTestCaseName = targetTestCaseName;
    }


    public List<SuspiciousVariable> findSuspiciousVariableInAssertLine() throws NoSuchFileException {
        //失敗テストを実行し、失敗したAssert行、またはクラッシュ時に最後に実行された行（失敗行）を取得
        TestLauncherForFinder.TestFailureInfo info = getAssertLine().orElse(null);
        if(info == null) return Collections.emptyList();

        //失敗行のロケーション情報を取得
        int failureLine = info.line();
        MethodElementName locateClass = info.locateClass();
        MethodElementName locateMethod = StaticAnalyzer.getMethodNamesWithLine(locateClass).get(failureLine);

        //ログ
        System.out.println("****** failure test: " + targetTestCaseName + "   location:  " + locateMethod + " ( line: "+ failureLine +" ) " + "*****************");

        //失敗行に含まれる変数の情報を抽出する。
        //assert文の場合、まずAssert文で使われてる変数全て取ってくる(actualかどうか考えない)
        List<SuspiciousVariable> result = new ArrayList<>();
        Statement stmtInFailureLine = extractStmtInFailureLine(failureLine, locateMethod);
        result.addAll(watchAllValuesInAssertLine(failureLine, locateMethod));
        List<String> neighborVariableNames = extractNeighborVariableNames(stmtInFailureLine);
        result = result.stream().filter(sv -> neighborVariableNames.contains(sv.getSimpleVariableName()))
                .collect(Collectors.toList());

        List<SuspiciousExpression> returns = searchSuspiciousReturns(failureLine, locateMethod, getMethodCallCount(stmtInFailureLine));
        for (SuspiciousExpression r : returns) {
            List<SuspiciousVariable> neighbor = r.neighborSuspiciousVariables(2000, false);
            result.addAll(neighbor);
        }

        return result;
    }

    private Optional<TestLauncherForFinder.TestFailureInfo> getAssertLine(){
        TestLauncherForFinder tl = new TestLauncherForFinder(targetTestCaseName);
        return tl.runTestAndGetFailureLine();
    }

    private List<SuspiciousExpression> searchSuspiciousReturns(int failureLine, MethodElementName locateMethod, int callCount){
        List<SuspiciousExpression> result = new ArrayList<>();
        Deque<SuspiciousExpression> suspExprQueue = new ArrayDeque<>(returnsInAssert(failureLine, locateMethod, callCount));

        System.out.println("------------------------------------------------------------------------------------------------------------");
        while(!suspExprQueue.isEmpty()){
            SuspiciousExpression target = suspExprQueue.removeFirst();

            List<SuspiciousReturnValue> returnsOfTarget = target.searchSuspiciousReturns();
            if(!returnsOfTarget.isEmpty()) {
                System.out.println(" >>> search return line");
                System.out.println(" >>> target: " + target);
                System.out.println(" >>> ");
                System.out.println(" >>> return lines");
                for (SuspiciousReturnValue r : returnsOfTarget) {
                    System.out.println(" >>> " + r);
                }
                target.addChild(returnsOfTarget);
                suspExprQueue.addAll(returnsOfTarget);
            }
            result.add(target);
        }

        return result.stream().distinct().toList();
    }


    protected List<SuspiciousVariable> watchAllVariablesInLine(StackFrame frame, MethodElementName locateMethod){
        List<SuspiciousVariable> result = new ArrayList<>();

        // （1）ローカル変数
        List<LocalVariable> locals;
        try {
            locals = frame.visibleVariables();
        } catch (AbsentInformationException e) {
            throw new RuntimeException(e);
        }
        Map<LocalVariable, Value> localVals = frame.getValues(locals);
        localVals.forEach((lv, v) -> {
            //配列の場合[0]のみ観測
            if(v instanceof ArrayReference ar){
                if(ar.length() == 0) return;
                if(ar.getValue(0) == null) return;
                result.add(new SuspiciousVariable(
                        this.targetTestCaseName,
                        locateMethod.getFullyQualifiedMethodName(),
                        lv.name() + "[0]",
                        ar.getValue(0).toString(),
                        true,
                        false,
                        0
                ));
            }
            if(v instanceof PrimitiveValue || (v != null && v.type().name().equals("java.lang.String"))) {
                result.add(new SuspiciousVariable(
                        this.targetTestCaseName,
                        locateMethod.getFullyQualifiedMethodName(),
                        lv.name(),
                        v.toString(),
                        true,
                        false
                ));
            }
        });

        // (2) インスタンスフィールド
        ObjectReference thisObj = frame.thisObject();
        if (thisObj != null) {
            ReferenceType  rt = thisObj.referenceType();
            for (Field f : rt.visibleFields()) {
                if (f.isStatic()) continue;
                if(thisObj.getValue(f) == null) continue;

                result.add(new SuspiciousVariable(
                        this.targetTestCaseName,
                        locateMethod.getFullyQualifiedMethodName(),
                        f.name(),
                        thisObj.getValue(f).toString(),
                        true,
                        true
                ));
            }
        }
        return result;
    }


    /**
     * 失敗行でsuspendした際に観測可能な変数を全て取得し、SuspiciousVariableとして返す。
     * @param failureLine
     * @param locateMethod
     * @return　観測された変数
     */
    private List<SuspiciousVariable> watchAllValuesInAssertLine(int failureLine, MethodElementName locateMethod){
        final List<SuspiciousVariable> result = new ArrayList<>();
        //Debugger生成
        String main = TestUtil.getJVMMain(this.targetTestCaseName);
        String options = TestUtil.getJVMOption();
        EnhancedDebugger eDbg = new EnhancedDebugger(main, options);

        //失敗行にブレークポイントを置きsuspend
        EnhancedDebugger.BreakpointHandler handler = (vm, bpe) -> {
            try {
                ThreadReference thread = bpe.thread();
                //その行が実際には複数回実行される可能性がある。
                //その際には失敗する時の情報（つまり、一番最後に実行された時の情報）を取得する。
                //そのために、まずresultを初期化することで暫定の最終実行時の情報がresultに保持される。
                result.clear();

                result.addAll(watchAllVariablesInLine(thread.frame(0), locateMethod));
            } catch (IncompatibleThreadStateException e) {
                throw new RuntimeException(e);
            }
        };

        //VMを実行し情報を収集
        eDbg.handleAtBreakPoint(locateMethod.getFullyQualifiedClassName(), failureLine, handler);
        return result;
    }

    private List<SuspiciousReturnValue> returnsInAssert(int failureLine, MethodElementName locateMethod, int callCount){
        final List<SuspiciousReturnValue> result = new ArrayList<>();

        //Debugger生成
        String main = TestUtil.getJVMMain(this.targetTestCaseName);
        String options = TestUtil.getJVMOption();
        EnhancedDebugger eDbg = new EnhancedDebugger(main, options);

        //対象の引数が属する行にたどり着いた時に行う処理を定義
        //ここではその行で呼ばれてるメソッド情報を抽出
        EnhancedDebugger.BreakpointHandler handler = (vm, bpe) -> {
            List<SuspiciousReturnValue> resultCandidate = new ArrayList<>();
            EventRequestManager manager = vm.eventRequestManager();

            ThreadReference thread = bpe.thread();
            /**
             * 方針
             * あらかじめその行で行われるメソッド呼び出しの回数を特定し、その回数だけ以下を行う。
             * 1. ブレークポイント行からStepInする
             * 2. その地点のクラス、メソッド名を取得する。
             * 3. そのクラス名でフィルターをかけたMethodExitRequestを生成、有効化する。
             * 4. MethodExitEventを捕捉し、メソッド名が一致していればそれを使う。
             * 5. stepOutし 1.に戻る。
             */


            for (int i = 0; i < callCount; i++) {
                // 1. ブレークポイント行からStepInする
                StepRequest stepIn = manager.createStepRequest(
                        thread,
                        StepRequest.STEP_LINE,
                        StepRequest.STEP_INTO
                );
                stepIn.addCountFilter(1);  // 次の１ステップで止まる
                stepIn.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                stepIn.enable();
                vm.resume();

                // 2. その地点のクラス、メソッド名を取得する。
                String locateClassName = null;
                String locateMethodName = null;
                EventSet es = vm.eventQueue().remove();
                for (Event ev : es) {
                    if (ev instanceof StepEvent se) {
                        stepIn.disable();
                        locateClassName = se.location().declaringType().name();
                        locateMethodName = se.location().method().name();
                    }
                }

                //もしjava.*等なら飛ばす。
                if(locateClassName.startsWith("java.") || locateClassName.startsWith("sun.") || locateClassName.startsWith("jdk.")) {
                    i--;
                }
                else {
                    // 3. そのクラス名でフィルターをかけたMethodExitRequestを生成、有効化する。
                    MethodExitRequest methodExitRequest = manager.createMethodExitRequest();
                    methodExitRequest.addClassFilter(locateClassName);
                    methodExitRequest.addThreadFilter(thread);
                    methodExitRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                    methodExitRequest.enable();

                    //4. MethodExitEventを捕捉し、メソッド名が一致していればそれを使う。
                    vm.resume();
                    boolean done = false;
                    while (!done) {
                        es = vm.eventQueue().remove();
                        for (Event ev : es) {
                            if (ev instanceof MethodExitEvent mee) {
                                String meeMethodName = mee.method().name();
                                if (mee.method().name().equals(locateMethodName)) {
                                    methodExitRequest.disable();
                                    done = true;
                                    //ブレークポイント行で直接呼び出されているMethodの名前、return行、実際の返り値を取得
                                    MethodElementName invokedMethod = new MethodElementName(EnhancedDebugger.getFqmn(mee.method()));
                                    int locateLine = mee.location().lineNumber();
                                    String actualValue = SuspiciousExpression.getValueString(mee.returnValue());
                                    try {
                                        SuspiciousReturnValue suspReturn = new SuspiciousReturnValue(
                                                this.targetTestCaseName,
                                                invokedMethod,
                                                locateLine,
                                                actualValue
                                        );
                                        resultCandidate.add(suspReturn);
                                    } catch (RuntimeException e) {
                                        System.out.println("cannot create SuspiciousReturnValue: " + e.getMessage() + " at " + invokedMethod + " line:" + locateLine);
                                    }
                                    break;
                                }
                                else {
                                    vm.resume();
                                }
                            }

                            else if (ev instanceof VMDeathEvent || ev instanceof VMDisconnectEvent) {
                                System.out.println("VM is disconnected before exit of method: " + locateClassName + "#" + locateMethodName);
                                result.clear();
                                result.addAll(resultCandidate);
                                return;
                            }
                        }
                    }
                }

                //stepOutしブレークポイント行に戻る。
                StepRequest stepOut = EnhancedDebugger.createStepOutRequest(manager, thread);
                vm.resume();
                vm.eventQueue().remove();
                stepOut.disable();
            }
            result.clear();
            result.addAll(resultCandidate);
        };

        //VMを実行し情報を収集
        eDbg.handleAtBreakPoint(locateMethod.getFullyQualifiedClassName(), failureLine, handler);
        return result;
    }

    protected static int getCallStackDepth(ThreadReference th){
        try {
            return th.frameCount();
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException(e);
        }
    }


    private Statement extractStmtInFailureLine(int failureLine, MethodElementName locateMethod){
        try {
            return JavaParserUtil.getStatementByLine(locateMethod, failureLine).orElseThrow();
        } catch (NoSuchFileException e) {
            throw new RuntimeException("Class [" + locateMethod + "] is not found.");
        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract Statement from [" + locateMethod + ":" + failureLine + "].");
        }
    }

    protected List<String> extractNeighborVariableNames(Statement stmt){
        return stmt.findAll(NameExpr.class).stream()
                //引数やメソッド呼び出しに用いられる変数を除外
                .map(NameExpr::toString)
                .collect(Collectors.toList());
    }

    private int getMethodCallCount(Statement stmt){
        return stmt.findAll(MethodCallExpr.class).size();
    }

}
