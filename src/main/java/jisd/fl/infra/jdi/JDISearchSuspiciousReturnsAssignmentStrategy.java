package jisd.fl.infra.jdi;

import com.sun.jdi.*;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.fl.core.domain.port.SearchSuspiciousReturnsStrategy;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.core.entity.susp.*;
import jisd.fl.infra.javaparser.JavaParserSuspiciousExpressionFactory;
import jisd.fl.infra.jdi.testexec.JDIDebugServerHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class JDISearchSuspiciousReturnsAssignmentStrategy implements SearchSuspiciousReturnsStrategy {
    private static final Logger logger = LoggerFactory.getLogger(JDISearchSuspiciousReturnsAssignmentStrategy.class);
    private final SuspiciousExpressionFactory factory = new JavaParserSuspiciousExpressionFactory();

    // 状態フィールド
    private List<SuspiciousExpression> result;
    private List<SuspiciousReturnValue> resultCandidate;
    private SuspiciousAssignment currentTarget;
    private ThreadReference targetThread;
    private MethodEntryRequest activeMethodEntryRequest;
    private MethodExitRequest activeMethodExitRequest;
    private StepRequest activeStepRequest;

    // StepIn/StepOut 状態管理
    private boolean steppingIn;        // true: StepIn 完了待ち, false: StepOut 完了待ち
    private boolean collectingReturn;  // true: MethodExitEvent 待ち
    private int enteredMethodDepth;    // 入ったメソッドの深さ
    private int depthAtBreakpoint;     // ブレークポイント地点でのコールスタックの深さ
    private int callCount;             // 直接呼び出しの回数

    @Override
    public List<SuspiciousExpression> search(SuspiciousExpression suspExpr) {
        // 状態の初期化
        this.currentTarget = (SuspiciousAssignment) suspExpr;
        this.result = new ArrayList<>();
        this.resultCandidate = new ArrayList<>();
        this.targetThread = null;
        this.activeMethodEntryRequest = null;
        this.activeMethodExitRequest = null;
        this.activeStepRequest = null;
        this.steppingIn = false;
        this.collectingReturn = false;
        this.enteredMethodDepth = 0;
        this.depthAtBreakpoint = 0;
        this.callCount = 0;

        if (!currentTarget.hasMethodCalling()) return result;

        // Debugger生成
        EnhancedDebugger debugger = JDIDebugServerHandle.createSharedDebugger(currentTarget.failedTest());

        // ハンドラ登録
        debugger.registerEventHandler(BreakpointEvent.class,
                (vm, ev) -> handleBreakpoint(vm, (BreakpointEvent) ev));
        debugger.registerEventHandler(MethodEntryEvent.class,
                (vm, ev) -> handleMethodEntry(vm, (MethodEntryEvent) ev));
        debugger.registerEventHandler(MethodExitEvent.class,
                (vm, ev) -> handleMethodExit(vm, (MethodExitEvent) ev));
        debugger.registerEventHandler(StepEvent.class,
                (vm, ev) -> handleStep(vm, (StepEvent) ev));

        // ブレークポイント設定と実行
        debugger.setBreakpoints(currentTarget.locateMethod().fullyQualifiedClassName(), List.of(currentTarget.locateLine()));
        debugger.execute(() -> !result.isEmpty());

        if (result.isEmpty()) {
            logger.warn("戻り値の確認に失敗: actualValue={}, method={}, line={}",
                    currentTarget.actualValue(), currentTarget.locateMethod(), currentTarget.locateLine());
        }
        return result;
    }

    private void handleBreakpoint(VirtualMachine vm, BreakpointEvent bpe) {
        // 既に結果が取得できている場合は終了
        if (!result.isEmpty()) return;

        EventRequestManager manager = vm.eventRequestManager();
        targetThread = bpe.thread();

        // 検索状態をリセット
        resultCandidate.clear();
        callCount = 0;
        steppingIn = true;
        collectingReturn = false;

        // ブレークポイント地点でのコールスタックの深さを取得
        depthAtBreakpoint = JDIUtils.getCallStackDepth(targetThread);

        // 前回の MethodEntryRequest を無効化
        if (activeMethodEntryRequest != null) {
            activeMethodEntryRequest.disable();
        }

        // MethodEntryRequest を作成（直接呼び出しのカウント用）
        activeMethodEntryRequest = EnhancedDebugger.createMethodEntryRequest(manager, targetThread);

        // メソッド呼び出しに入るための StepInRequest を作成
        activeStepRequest = EnhancedDebugger.createStepInRequest(manager, targetThread);
    }

    private void handleMethodEntry(VirtualMachine vm, MethodEntryEvent mEntry) {
        // 対象スレッドでない場合はスキップ
        if (!mEntry.thread().equals(targetThread)) return;

        // 直接呼び出し（depth == depthAtBreakpoint + 1）のみカウント
        int currentDepth = JDIUtils.getCallStackDepth(mEntry.thread());
        if (currentDepth != depthAtBreakpoint + 1) return;

        callCount++;
    }

    private void handleMethodExit(VirtualMachine vm, MethodExitEvent mee) {
        // 戻り値収集中でない場合はスキップ
        if (!collectingReturn) return;

        // 対象スレッドでない場合はスキップ
        if (!mee.thread().equals(targetThread)) return;

        // 入ったメソッドからの終了のみ収集（深さで判定）
        if (JDIUtils.getCallStackDepth(mee.thread()) == enteredMethodDepth) {
            collectReturnValue(mee);
            // MethodExitRequest を無効化（このメソッドの戻り値を取得したので）
            activeMethodExitRequest.disable();
            activeMethodExitRequest = null;
            collectingReturn = false;
        }
    }

    private void handleStep(VirtualMachine vm, StepEvent se) {
        // 既に結果が取得できている場合はスキップ
        if (!result.isEmpty()) return;

        EventRequestManager manager = vm.eventRequestManager();
        ThreadReference thread = se.thread();

        // 現在の StepRequest を削除（同一スレッドに複数の StepRequest は作成できない）
        manager.deleteEventRequest(activeStepRequest);
        activeStepRequest = null;

        if (steppingIn) {
            handleStepInCompleted(manager, thread, se);
        } else {
            handleStepOutCompleted(manager, thread, se);
        }
    }

    /**
     * StepIn 完了後の処理。メソッドに入った可能性がある。
     */
    private void handleStepInCompleted(EventRequestManager manager, ThreadReference thread, StepEvent se) {
        // 対象位置からの呼び出しかを確認
        if (!isCalledFromTargetLocation(thread)) {
            // メソッドに入っていない、または対象位置からの呼び出しでない
            // → 行の実行が終わった可能性があるので検証
            if (JDIUtils.validateIsTargetExecution(se, currentTarget.assignTarget)) {
                result.addAll(resultCandidate);
            }
            // 結果が空の場合は次の BreakpointEvent を待つ
            return;
        }

        // 直接呼び出しのメソッドに入った
        enteredMethodDepth = JDIUtils.getCallStackDepth(thread);

        // このメソッドの戻り値を取得するための MethodExitRequest を作成
        activeMethodExitRequest = EnhancedDebugger.createMethodExitRequest(manager, thread);
        collectingReturn = true;

        // メソッドから抜けるための StepOutRequest を作成
        activeStepRequest = EnhancedDebugger.createStepOutRequest(manager, thread);
        steppingIn = false;
    }

    /**
     * StepOut 完了後の処理。呼び出し元に戻った状態。
     */
    private void handleStepOutCompleted(EventRequestManager manager, ThreadReference thread, StepEvent se) {
        int currentLine = se.location().lineNumber();

        if (currentLine == currentTarget.locateLine()) {
            // まだ同じ行にいる → 次のメソッド呼び出しを探す
            steppingIn = true;
            activeStepRequest = EnhancedDebugger.createStepInRequest(manager, thread);
        } else {
            // 行を離れた → 行の実行が完了したので検証
            if (JDIUtils.validateIsTargetExecution(se, currentTarget.assignTarget)) {
                result.addAll(resultCandidate);
            }
            // 結果が空の場合は次の BreakpointEvent を待つ
        }
    }

    private boolean isCalledFromTargetLocation(ThreadReference thread) {
        return JDIUtils.isCalledFromTargetLocation(thread, currentTarget.locateMethod(), currentTarget.locateLine());
    }

    private void collectReturnValue(MethodExitEvent mee) {
        // targetReturnCallPositions に含まれる直接呼び出しのみ収集
        if (!currentTarget.targetReturnCallPositions.contains(callCount)) {
            return;
        }
        JDIUtils.createSuspiciousReturnValue(mee, currentTarget.failedTest(), factory)
                .ifPresent(resultCandidate::add);
    }
}
