package jisd.fl.infra.jdi;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import jisd.fl.core.domain.port.TraceValueAtSuspiciousExpressionStrategy;
import jisd.fl.core.entity.susp.SuspiciousArgument;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.infra.junit.JUnitDebugger;

import java.util.ArrayList;
import java.util.List;

public class JDITraceValueAtSuspiciousArgumentStrategy implements TraceValueAtSuspiciousExpressionStrategy {

    // 状態フィールド
    private List<TracedValue> result;
    private List<TracedValue> resultCandidate;
    private SuspiciousArgument currentTarget;
    private MethodEntryRequest activeMethodEntryRequest;
    private boolean targetMethodFound;

    public List<TracedValue> traceAllValuesAtSuspExpr(SuspiciousExpression suspExpr){
        // 状態の初期化
        this.currentTarget = (SuspiciousArgument) suspExpr;
        this.result = new ArrayList<>();
        this.resultCandidate = null;
        this.activeMethodEntryRequest = null;
        this.targetMethodFound = false;

        // Debugger生成
        JUnitDebugger debugger = new JUnitDebugger(currentTarget.failedTest);

        // ハンドラ登録
        debugger.registerEventHandler(BreakpointEvent.class,
                (vm, ev) -> handleBreakpoint(vm, (BreakpointEvent) ev));
        debugger.registerEventHandler(MethodEntryEvent.class,
                (vm, ev) -> handleMethodEntry(vm, (MethodEntryEvent) ev));

        // ブレークポイント設定と実行
        debugger.setBreakpoints(currentTarget.locateMethod.fullyQualifiedClassName(), List.of(currentTarget.locateLine));
        debugger.execute(() -> !result.isEmpty());
        return result;
    }

    private void handleBreakpoint(VirtualMachine vm, BreakpointEvent bpe) {
        // 既に情報が取得できている場合は終了
        if (!result.isEmpty()) return;

        // 前回の検索状態をリセット
        this.targetMethodFound = false;

        EventRequestManager manager = vm.eventRequestManager();

        // メソッドの呼び出しが行われたことを検知する MethodEntryRequest を作成
        // 目的の行であったかの判断は、メソッドに入った時の引数の値で確認する
        ThreadReference thread = bpe.thread();
        activeMethodEntryRequest = EnhancedDebugger.createMethodEntryRequest(manager, thread);

        // 周辺の値を観測
        try {
            StackFrame frame = thread.frame(0);
            resultCandidate = JDIUtils.watchAllVariablesInLine(frame, currentTarget.locateLine);
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException(e);
        }
        // execute() のループが resume を処理
    }

    private void handleMethodEntry(VirtualMachine vm, MethodEntryEvent mEntry) {
        // 既に結果が取得できている、または今回のブレークポイントで対象メソッドが見つかった場合はスキップ
        if (!result.isEmpty() || targetMethodFound) return;

        // 1) 通常メソッドの場合は name() で比較
        // 2) コンストラクタの場合は declaringType().name()（FQCN）で比較
        boolean isTarget;
        Method method = mEntry.method();
        if (method.isConstructor()) {
            // calleeMethodName には FullyQualifiedClassName を保持している想定
            isTarget = method.declaringType().name()
                    .equals(currentTarget.calleeMethodName.fullyQualifiedClassName());
        } else {
            isTarget = method.name().equals(currentTarget.calleeMethodName.shortMethodName());
        }

        // entryしたメソッドが目的のcalleeメソッドか確認
        if (isTarget) {
            if (JDIUtils.validateIsTargetExecutionArg(mEntry, currentTarget.actualValue, currentTarget.argIndex)) {
                targetMethodFound = true;
                result.addAll(resultCandidate);
                activeMethodEntryRequest.disable();
            }
            // 引数が一致しない場合は検索を続行（同一行の次のメソッド呼び出しを待つ）
        }
        // isTarget が false の場合も execute() のループが resume を処理し、次の MethodEntryEvent を待つ
    }
}
