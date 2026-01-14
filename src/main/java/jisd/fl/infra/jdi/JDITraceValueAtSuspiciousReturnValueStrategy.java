package jisd.fl.infra.jdi;

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
import jisd.fl.core.domain.port.TraceValueAtSuspiciousExpressionStrategy;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.susp.SuspiciousReturnValue;
import jisd.fl.probe.record.TracedValue;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.probe.record.TracedValuesAtLine;
import jisd.fl.util.TestUtil;

import java.util.ArrayList;
import java.util.List;

public class JDITraceValueAtSuspiciousReturnValueStrategy implements TraceValueAtSuspiciousExpressionStrategy {
    public TracedValueCollection traceAllValuesAtSuspExpr(SuspiciousExpression suspExpr){
        SuspiciousReturnValue suspReturn = (SuspiciousReturnValue) suspExpr;
        System.out.println(" >>> [DEBUG] Return");
        final List<TracedValue> result = new ArrayList<>();

        //Debugger生成
        String main = TestUtil.getJVMMain(suspReturn.failedTest);
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
                resultCandidate = JDIUtils.watchAllVariablesInLine(frame, suspReturn.locateLine);
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
                        System.out.println(" >>> [DEBUG] Return: " + JDIUtils.getValueString(recentMee.returnValue()));
                        if(JDIUtils.validateIsTargetExecution(recentMee, suspReturn.actualValue)) result.addAll(resultCandidate);
                        //vmをresumeしない
                    }
                }
            }
            //動的に作ったリクエストを無効化
            stepReq.disable();
            meReq.disable();
        };

        //VMを実行し情報を収集
        eDbg.handleAtBreakPoint(suspReturn.locateMethod.getFullyQualifiedClassName(), suspReturn.locateLine, handler);
        return TracedValuesAtLine.of(result);
    }
}
