package jisd.fl.infra.jdi;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.fl.core.domain.port.SuspiciousArgumentsSearcher;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.infra.javaparser.JavaParserSuspiciousExpressionFactory;
import jisd.fl.core.entity.susp.SuspiciousArgument;
import jisd.fl.infra.jdi.testexec.JDIDebugServerHandle;

import java.util.List;
import java.util.Optional;

import com.sun.jdi.request.EventRequest;

public class JDISuspiciousArgumentsSearcher implements SuspiciousArgumentsSearcher {
    static public final SuspiciousExpressionFactory factory = new JavaParserSuspiciousExpressionFactory();

    // ステートマシンのフェーズ
    private enum Phase {
        WAITING_FOR_TARGET,  // ターゲットメソッドの MethodEntry を待機
        STEPPING_OUT,        // StepOut でターゲットメソッドから呼び出し元に戻る
        COUNTING_CALLS,      // 行内のメソッド呼び出し回数をカウント
        COMPLETED            // 完了
    }

    // 状態フィールド
    private Phase currentPhase;
    private ThreadReference targetThread;
    private int depthBeforeCall;
    private StepRequest activeStepRequest;
    private MethodExitRequest activeMethodExitRequest;

    // 結果フィールド
    private MethodElementName locateMethod;
    private int locateLine;
    private int argIndex;
    private int callCountAfterTarget;
    private boolean found;

    // 入力パラメータ（ハンドラから参照）
    private SuspiciousLocalVariable suspVar;
    private MethodElementName invokeMethodName;

    /**
     * ある変数がその値を取る原因が呼び出し元の引数にあると判明した場合に使用
     */
    public Optional<SuspiciousArgument> searchSuspiciousArgument(SuspiciousLocalVariable suspVar, MethodElementName invokeMethodName) {
        // 状態の初期化
        this.suspVar = suspVar;
        this.invokeMethodName = invokeMethodName;
        this.currentPhase = Phase.WAITING_FOR_TARGET;
        this.targetThread = null;
        this.depthBeforeCall = 0;
        this.activeStepRequest = null;
        this.activeMethodExitRequest = null;
        this.locateMethod = null;
        this.locateLine = 0;
        this.argIndex = -1;
        this.callCountAfterTarget = 0;
        this.found = false;

        // Debugger生成
        EnhancedDebugger debugger = JDIDebugServerHandle.createSharedDebugger(suspVar.failedTest());

        // MethodEntryRequest を設定
        EventRequestManager manager = debugger.vm.eventRequestManager();
        MethodEntryRequest methodEntryRequest = manager.createMethodEntryRequest();
        methodEntryRequest.addClassFilter(invokeMethodName.fullyQualifiedName().split("#")[0]);
        methodEntryRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        methodEntryRequest.enable();

        // ハンドラ登録
        debugger.registerEventHandler(MethodEntryEvent.class,
                (vm, ev) -> handleMethodEntry(vm, (MethodEntryEvent) ev, methodEntryRequest));
        debugger.registerEventHandler(StepEvent.class,
                (vm, ev) -> handleStepEvent(vm, (StepEvent) ev));
        debugger.registerEventHandler(MethodExitEvent.class,
                (vm, ev) -> handleMethodExit((MethodExitEvent) ev));

        debugger.execute(() -> found);

        // 結果チェック
        if (locateMethod == null || locateLine == 0 || argIndex == -1) {
            System.err.println("Cannot find target argument of caller method. (may not be argument)\n" + suspVar);
            return Optional.empty();
        }
        return Optional.of(factory.createArgument(
                suspVar.failedTest(),
                locateMethod,
                locateLine,
                suspVar.actualValue(),
                invokeMethodName,
                argIndex,
                callCountAfterTarget
        ));
    }

    /**
     * Phase 1: ターゲットメソッドの MethodEntry を検出
     */
    private void handleMethodEntry(VirtualMachine vm, MethodEntryEvent mEntry, MethodEntryRequest methodEntryRequest) {
        if (currentPhase != Phase.WAITING_FOR_TARGET) return;

        // ターゲットのメソッドでない場合は無視
        String targetFqmn = EnhancedDebugger.getFqmn(mEntry.method());
        if (!targetFqmn.equals(invokeMethodName.fullyQualifiedName())) return;

        try {
            // 呼び出しメソッドを取得
            ThreadReference thread = mEntry.thread();
            StackFrame topFrame = thread.frame(0);
            StackFrame callerFrame = thread.frame(1);

            // 調査対象の変数が actualValue をとっているか確認
            LocalVariable topVar = topFrame.visibleVariableByName(suspVar.variableName());
            if (topVar == null) return;
            Value argValue = topFrame.getValue(topVar);
            if (!JDIUtils.getValueString(argValue).equals(suspVar.actualValue())) return;

            // 対象の引数のインデックスを取得
            List<LocalVariable> args = mEntry.method().arguments();
            int foundArgIndex = -1;
            for (int idx = 0; idx < args.size(); idx++) {
                if (args.get(idx).name().equals(suspVar.variableName())) {
                    foundArgIndex = idx;
                    break;
                }
            }
            // 引数に含まれない場合
            if (foundArgIndex == -1) {
                return;
            }
            this.argIndex = foundArgIndex;

            // 呼び出し元の位置を記録
            Location callerLoc = callerFrame.location();
            this.locateMethod = new MethodElementName(EnhancedDebugger.getFqmn(callerLoc.method()));
            this.locateLine = callerLoc.lineNumber();

            // MethodEntryRequest を無効化し、StepOut を開始
            methodEntryRequest.disable();
            this.targetThread = thread;

            EventRequestManager manager = vm.eventRequestManager();
            this.activeStepRequest = EnhancedDebugger.createStepOutRequest(manager, thread);
            this.currentPhase = Phase.STEPPING_OUT;

        } catch (AbsentInformationException | IncompatibleThreadStateException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Phase 2-3: StepOut/StepOver の処理
     */
    private void handleStepEvent(VirtualMachine vm, StepEvent se) {
        if (targetThread == null || !se.thread().equals(targetThread)) return;

        EventRequestManager manager = vm.eventRequestManager();

        if (currentPhase == Phase.STEPPING_OUT) {
            // StepOut 完了 - 呼び出し元の行に戻った
            activeStepRequest.disable();
            activeStepRequest = null;

            // コールスタックの深さを記録
            this.depthBeforeCall = JDIUtils.getCallStackDepth(se.thread());

            // StepOver + MethodExit でカウント開始
            this.activeStepRequest = EnhancedDebugger.createStepOverRequest(manager, se.thread());
            this.activeMethodExitRequest = EnhancedDebugger.createMethodExitRequest(manager, se.thread());
            this.currentPhase = Phase.COUNTING_CALLS;

        } else if (currentPhase == Phase.COUNTING_CALLS) {
            // StepOver 完了 - 行の実行が終了
            disableRequests();
            this.found = true;
            this.currentPhase = Phase.COMPLETED;
        }
    }

    /**
     * Phase 3: 直接呼び出しの回数をカウント
     */
    private void handleMethodExit(MethodExitEvent mee) {
        if (currentPhase != Phase.COUNTING_CALLS) return;
        if (targetThread == null || !mee.thread().equals(targetThread)) return;

        // 直接呼び出し（depth == depthBeforeCall + 1）のみカウント
        int currentDepth = JDIUtils.getCallStackDepth(mee.thread());
        if (currentDepth == depthBeforeCall + 1) {
            callCountAfterTarget++;
        }
    }

    /**
     * 動的に作成したリクエストを無効化する。
     */
    private void disableRequests() {
        if (activeStepRequest != null) {
            activeStepRequest.disable();
            activeStepRequest = null;
        }
        if (activeMethodExitRequest != null) {
            activeMethodExitRequest.disable();
            activeMethodExitRequest = null;
        }
    }
}
