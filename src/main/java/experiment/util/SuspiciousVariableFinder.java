package experiment.util;

import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.debug.*;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousReturnValue;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.*;

import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.stream.Collectors;

public class SuspiciousVariableFinder {
    private final MethodElementName targetTestCaseName;

    public SuspiciousVariableFinder(MethodElementName targetTestCaseName) throws NoSuchFileException {
        this.targetTestCaseName = targetTestCaseName;
    }

    private Optional<TestLauncherForFinder.TestFailureInfo> getAssertLine(){
        TestLauncherForFinder tl = new TestLauncherForFinder(targetTestCaseName);
        return tl.runTestAndGetFailureLine();
    }

    public List<SuspiciousVariable> find() throws NoSuchFileException {
        TestLauncherForFinder.TestFailureInfo info = getAssertLine().orElse(null);
        if(info == null) return Collections.emptyList();

        int failureLine = info.line();
        MethodElementName locateClass = info.locateClass();
        MethodElementName locateMethod = StaticAnalyzer.getMethodNamesWithLine(locateClass).get(failureLine);

        System.out.println("failure test: " + targetTestCaseName);

        System.out.println("failure location: [method] " + locateMethod + " [line] " + failureLine);


        List<SuspiciousVariable> result = new ArrayList<>();

        //assert文の場合、まずAssert文で使われてる変数全て取ってくる(actualかどうか考えない)
        result.addAll(valuesInAssert(failureLine, locateMethod));
        Statement stmt = extractStmt(failureLine, locateMethod);
        List<String> neighborVariableNames = extractNeighborVariableNames(stmt);
        result = result.stream().filter(sv -> neighborVariableNames.contains(sv.getSimpleVariableName()))
                .collect(Collectors.toList());

        List<SuspiciousExpression> returns = searchSuspiciousReturns(failureLine, locateMethod);
        for (SuspiciousExpression r : returns) {
            List<SuspiciousVariable> neighbor = r.neighborSuspiciousVariables(2000, false);
            result.addAll(neighbor);
        }

        return result;
    }

    private List<SuspiciousExpression> searchSuspiciousReturns(int failureLine, MethodElementName locateMethod){
        List<SuspiciousExpression> result = new ArrayList<>();
        Deque<SuspiciousExpression> suspExprQueue = new ArrayDeque<>(returnsInAssert(failureLine, locateMethod));

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

    private List<SuspiciousVariable> valuesInAssert(int failureLine, MethodElementName locateMethod){
        final List<SuspiciousVariable> result = new ArrayList<>();
        //Debugger生成
        String main = TestUtil.getJVMMain(this.targetTestCaseName);
        String options = TestUtil.getJVMOption();
        EnhancedDebugger eDbg = new EnhancedDebugger(main, options);
        //調査対象の行実行に到達した時に行う処理を定義
        EnhancedDebugger.BreakpointHandler handler = (vm, bpe) -> {
            try {
                ThreadReference thread = bpe.thread();
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

    private List<SuspiciousReturnValue> returnsInAssert(int failureLine, MethodElementName locateMethod){
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

            //このスレッドでの MethodExit を記録するリクエストを作成
            ThreadReference thread = bpe.thread();
            MethodExitRequest meReq = EnhancedDebugger.createMethodExitRequest(manager, thread);
            //この行の実行が終わったことを検知するステップリクエストを作成
            StepRequest stepReq = EnhancedDebugger.createStepOverRequest(manager, thread);

            // ブレークポイント地点でのコールスタックの深さを取得
            // 呼び出しメソッドの取得条件を 深さ == depthBeforeCall + 1　にすることで
            // 再帰呼び出し含め、その行で直接呼ばれたメソッドのみ取ってこれる
            int depthBeforeCall = getCallStackDepth(thread);

            //一旦 resume して、内部ループで MethodExit／Step を待つ
            vm.resume();
            boolean done = false;
            while (!done) {
                EventSet es = vm.eventQueue().remove();
                for (Event ev : es) {
                    //実行された、とあるメソッドから抜けた
                    if (ev instanceof MethodExitEvent) {
                        MethodExitEvent mee = (MethodExitEvent) ev;

                        //収集するのは指定した行で直接呼び出したメソッドのみ
                        //depthBeforeCallとコールスタックの深さを比較することで直接呼び出したメソッドかどうかを判定
                        if (mee.thread().equals(thread) && getCallStackDepth(mee.thread()) == depthBeforeCall + 1) {
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
                            }
                            catch (RuntimeException e){
                                System.out.println("cannot create SuspiciousReturnValue: " + e.getMessage() + " at " + invokedMethod + " line:" + locateLine);
                            }
                        }
                        vm.resume();
                    }
                    //調査対象の行の実行(実行インスタンス)が終了
                    if (ev instanceof StepEvent) {
                        done = true;
                        result.clear();
                        result.addAll(resultCandidate);
                        //vmをresumeしない
                    }
                }
            }
            //動的に作ったリクエストを無効化
            meReq.disable();
            stepReq.disable();
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

    private Statement extractStmt(int failureLine, MethodElementName locateMethod){
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

}
