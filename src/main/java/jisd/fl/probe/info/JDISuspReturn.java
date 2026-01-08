package jisd.fl.probe.info;

import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.debug.EnhancedDebugger;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.infra.javaparser.JavaParserSuspiciousExpressionFactory;
import jisd.fl.probe.record.TracedValue;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.probe.record.TracedValuesAtLine;
import jisd.fl.util.TestUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class JDISuspReturn {
    static final SuspiciousExpressionFactory factory = new JavaParserSuspiciousExpressionFactory();
    public static List<SuspiciousReturnValue> searchSuspiciousReturns(SuspiciousReturnValue thisSuspReturn) throws NoSuchElementException {
        final List<SuspiciousReturnValue> result = new ArrayList<>();
        if(!thisSuspReturn.hasMethodCalling()) return result;

        //Debugger生成
        String main = TestUtil.getJVMMain(thisSuspReturn.failedTest);
        String options = TestUtil.getJVMOption();
        EnhancedDebugger eDbg = new EnhancedDebugger(main, options);
        //ブレークポイントにヒットした時に行う処理を定義
        //ここではその行で呼ばれてるメソッド情報を抽出
        EnhancedDebugger.BreakpointHandler handler = (vm, bpe) -> {
            //既に情報が取得できている場合は終了
            if(!result.isEmpty()) return;

            List<SuspiciousReturnValue> resultCandidate = new ArrayList<>();
            EventRequestManager manager = vm.eventRequestManager();

            //このスレッドでの MethodExit を記録するリクエストを作成
            ThreadReference thread = bpe.thread();
            MethodExitRequest meReq = EnhancedDebugger.createMethodExitRequest(manager, thread);
            //この行の実行が終わったことを検知するステップリクエストを作成
            //この具象クラスではステップイベントの通知タイミングで、今調査していた行が調べたい行だったかを確認
            StepRequest stepReq = EnhancedDebugger.createStepOutRequest(manager, thread);

            //直前に通知されたMethodExitEventを保持
            //StepEventでreturnから返った時にこのMEEを使ってreturnのactualValueを手にいれる。
            MethodExitEvent recentMee = null;

            // ブレークポイント地点でのコールスタックの深さを取得
            // 呼び出しメソッドの取得条件を 深さ == depthBeforeCall + 1　にすることで
            // 再帰呼び出し含め、その行で直接呼ばれたメソッドのみ取ってこれる
            int depthBeforeCall = TmpJDIUtils.getCallStackDepth(thread);
            //一旦 resume して、内部ループで MethodExit／Step を待つ
            vm.resume();
            boolean done = false;
            while (!done) {
                EventSet es = vm.eventQueue().remove();
                for (Event ev : es) {
                    //実行された、とあるメソッドから抜けた
                    if (ev instanceof MethodExitEvent) {
                        MethodExitEvent mee = (MethodExitEvent) ev;
                        recentMee = mee;
                        StackFrame caller = null;
                        try {
                            //thread()がsuspendされていないと例外を投げる
                            //普通は成功するはず
                            //waitForThreadPreparation(mee.thread());
                            caller = mee.thread().frame(1);
                        } catch (IncompatibleThreadStateException e) {
                            throw new RuntimeException("Target thread must be suspended.");
                        }

                        //収集するのは指定した行で直接呼び出したメソッドのみ
                        //depthBeforeCallとコールスタックの深さを比較することで直接呼び出したメソッドかどうかを判定
                        if (mee.thread().equals(thread) && TmpJDIUtils.getCallStackDepth(mee.thread()) == depthBeforeCall + 1) {
                            MethodElementName invokedMethod = new MethodElementName(EnhancedDebugger.getFqmn(mee.method()));
                            int locateLine = mee.location().lineNumber();
                            String actualValue = TmpJDIUtils.getValueString(mee.returnValue());
                            try {
                                SuspiciousReturnValue suspReturn = factory.createReturnValue(
                                        thisSuspReturn.failedTest,
                                        invokedMethod,
                                        locateLine,
                                        actualValue
                                );
                                resultCandidate.add(suspReturn);
                            } catch (RuntimeException e) {
                                System.out.println("cannot create SuspiciousReturnValue: " + e.getMessage() + " at " + invokedMethod + " line:" + locateLine);
                            }
                        }
                        vm.resume();
                    }
                    //調査対象の行の実行が終了
                    //ここで、調査した行が目的のものであったかチェック
                    if (ev instanceof StepEvent) {
                        done = true;
                        StepEvent se = (StepEvent) ev;
                        if(recentMee == null){
                            //ここには到達しないはず
                            throw new RuntimeException("Something is wrong.");
                        }
                        if(validateIsTargetExecution(recentMee, thisSuspReturn.actualValue)) result.addAll(resultCandidate);
                        //vmをresumeしない
                    }
                }
            }
            //動的に作ったリクエストを無効化
            meReq.disable();
            stepReq.disable();
        };

        //VMを実行し情報を収集
        eDbg.handleAtBreakPoint(thisSuspReturn.locateMethod.getFullyQualifiedClassName(), thisSuspReturn.locateLine, handler);
        if(result.isEmpty()){
            System.err.println("[[searchSuspiciousReturns]] Could not confirm [ "
                    + "(return value) == " + thisSuspReturn.actualValue
                    + " ] on " + thisSuspReturn.locateMethod + " line:" + thisSuspReturn.locateLine);
        }
        return result;
    }

    static TracedValueCollection traceAllValuesAtSuspExpr(int sleepTime, SuspiciousExpression thisSuspExpr){
        System.out.println(" >>> [DEBUG] Return");
        final List<TracedValue> result = new ArrayList<>();

        //Debugger生成
        String main = TestUtil.getJVMMain(thisSuspExpr.failedTest);
        String options = TestUtil.getJVMOption();
        EnhancedDebugger eDbg = new EnhancedDebugger(main, options);

        //対象の引数が属する行にたどり着いた時に行う処理を定義
        //ここではその行で呼ばれてるメソッド情報を抽出
        EnhancedDebugger.BreakpointHandler handler = (vm, bpe) -> {
            //既に情報が取得できている場合は終了
            if(!result.isEmpty()) return;

            EventRequestManager manager = vm.eventRequestManager();

            //この行の実行が終わったことを検知するステップリクエストを作成
            //この具象クラスではステップイベントの通知タイミングで、今調査していた行が調べたい行だったかを確認
            ThreadReference thread = bpe.thread();
            StepRequest stepReq = EnhancedDebugger.createStepOutRequest(manager, thread);
            MethodExitRequest meReq = EnhancedDebugger.createMethodExitRequest(manager, thread);

            //直前に通知されたMethodExitEventを保持
            //StepEventでreturnから返った時にこのMEEを使ってreturnのactualValueを手にいれる。
            MethodExitEvent recentMee = null;

            //周辺の値を観測
            List<TracedValue> resultCandidate;
            try {
                StackFrame frame = thread.frame(0);
                resultCandidate = TmpJDIUtils.watchAllVariablesInLine(frame, thisSuspExpr.locateLine);
            } catch (IncompatibleThreadStateException e) {
                throw new RuntimeException(e);
            }


            //resume してステップイベント/MethodExitを待つ
            vm.resume();
            boolean done = false;
            while (!done) {
                EventSet es = vm.eventQueue().remove();
                for (Event ev : es) {
                    if (ev instanceof MethodExitEvent) {
                        //直前に通知されたMethodExitEventを保持
                        MethodExitEvent mee = (MethodExitEvent) ev;
                        recentMee = mee;
                        vm.resume();
                    }
                    //調査対象の行の実行(実行インスタンス)が終了
                    //ここで、調査した行が目的のものであったかチェック
                    if (ev instanceof StepEvent) {
                        done = true;
                        if(recentMee == null){
                            //ここには到達しないはず
                            throw new RuntimeException("Something is wrong.");
                        }
                        System.out.println(" >>> [DEBUG] Return: " + TmpJDIUtils.getValueString(recentMee.returnValue()));
                        if(validateIsTargetExecution(recentMee, thisSuspExpr.actualValue)) result.addAll(resultCandidate);
                        //vmをresumeしない
                    }
                }
            }
            //動的に作ったリクエストを無効化
            stepReq.disable();
            meReq.disable();
        };

        //VMを実行し情報を収集
        eDbg.handleAtBreakPoint(thisSuspExpr.locateMethod.getFullyQualifiedClassName(), thisSuspExpr.locateLine, handler);
        return TracedValuesAtLine.of(result);
    }

    static private boolean validateIsTargetExecution(MethodExitEvent recent, String actualValue){
        return TmpJDIUtils.getValueString(recent.returnValue()).equals(actualValue);
    }
}
