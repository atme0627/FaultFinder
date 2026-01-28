package jisd.fl.infra.jdi;

import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.fl.core.domain.port.TraceValueAtSuspiciousExpressionStrategy;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.susp.SuspiciousReturnValue;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.infra.junit.JUnitDebugger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class JDITraceValueAtSuspiciousReturnValueStrategy implements TraceValueAtSuspiciousExpressionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(JDITraceValueAtSuspiciousReturnValueStrategy.class);

    // 状態フィールド
    private List<TracedValue> result;
    private List<TracedValue> resultCandidate;
    private SuspiciousReturnValue currentTarget;
    private StepRequest activeStepRequest;
    private MethodExitRequest activeMethodExitRequest;
    private String recentReturnValue;

    public List<TracedValue> traceAllValuesAtSuspExpr(SuspiciousExpression suspExpr) {
        // 状態の初期化
        this.currentTarget = (SuspiciousReturnValue) suspExpr;
        this.result = new ArrayList<>();
        this.resultCandidate = null;
        this.activeStepRequest = null;
        this.activeMethodExitRequest = null;
        this.recentReturnValue = null;

        //Debugger生成
        JUnitDebugger debugger = new JUnitDebugger(currentTarget.failedTest);

        // ハンドラ登録
        debugger.registerEventHandler(com.sun.jdi.event.BreakpointEvent.class,
                (vm, ev) -> handleBreakpoint(vm, (com.sun.jdi.event.BreakpointEvent) ev));
        debugger.registerEventHandler(MethodExitEvent.class,
                (vm, ev) -> handleMethodExit(vm, (MethodExitEvent) ev));
        debugger.registerEventHandler(StepEvent.class,
                (vm, ev) -> handleStep(vm, (StepEvent) ev));

        debugger.setBreakpoints(
                currentTarget.locateMethod.fullyQualifiedClassName(),
                List.of(currentTarget.locateLine)
        );

        //VMを実行し情報を収集
        debugger.execute(() -> !result.isEmpty());
        return result;
    }

    private void handleBreakpoint(com.sun.jdi.VirtualMachine vm, com.sun.jdi.event.BreakpointEvent bpe) {
        //既に情報が取得できている場合は終了
        if(!result.isEmpty()) return;

        // 次回の handleBreakpoint 用にリセット
        this.recentReturnValue = null;

        EventRequestManager manager = vm.eventRequestManager();

        //この行の実行が終わったことを検知するステップリクエストを作成
        //この具象クラスではステップイベントの通知タイミングで、今調査していた行が調べたい行だったかを確認
        ThreadReference thread = bpe.thread();
        this.activeStepRequest = EnhancedDebugger.createStepOutRequest(manager, thread);
        this.activeMethodExitRequest = EnhancedDebugger.createMethodExitRequest(manager, thread);

        //周辺の値を観測
        try {
            StackFrame frame = thread.frame(0);
            this.resultCandidate = JDIUtils.watchAllVariablesInLine(frame, currentTarget.locateLine);
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException(e);
        }
        // execute() の統合イベントループが resume と MethodExit/StepEvent 処理を行う
    }

    private void handleMethodExit(com.sun.jdi.VirtualMachine vm, MethodExitEvent mee) {
        //直前に通知されたMethodExitEventの戻り値を保持
        this.recentReturnValue = JDIUtils.getValueString(mee.returnValue());
        // execute() の統合イベントループが resume を行う
    }

    private void handleStep(com.sun.jdi.VirtualMachine vm, StepEvent se) {
        //調査対象の行の実行(実行インスタンス)が終了
        //ここで、調査した行が目的のものであったかチェック
        if(recentReturnValue == null){
            String msg = String.format(
                "StepEvent の前に MethodExitEvent が発生していません。" +
                "対象=%s:%d, actualValue=%s",
                currentTarget.locateMethod, currentTarget.locateLine, currentTarget.actualValue);
            logger.error(msg);
            throw new IllegalStateException(msg);
        }
        if(recentReturnValue.equals(currentTarget.actualValue)) {
            result.addAll(resultCandidate);
        }

        //動的に作ったリクエストを無効化
        activeStepRequest.disable();
        activeMethodExitRequest.disable();
        //vmをresumeしない（execute()の終了条件で停止）
    }
}
