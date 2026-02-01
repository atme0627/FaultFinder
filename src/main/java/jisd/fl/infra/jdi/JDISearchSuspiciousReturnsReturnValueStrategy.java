package jisd.fl.infra.jdi;

import com.sun.jdi.*;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.fl.core.domain.port.SearchSuspiciousReturnsStrategy;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.susp.SuspiciousReturnValue;
import jisd.fl.infra.javaparser.JavaParserSuspiciousExpressionFactory;
import jisd.fl.infra.jdi.testexec.JDIDebugServerHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class JDISearchSuspiciousReturnsReturnValueStrategy implements SearchSuspiciousReturnsStrategy {
    private static final Logger logger = LoggerFactory.getLogger(JDISearchSuspiciousReturnsReturnValueStrategy.class);
    private final SuspiciousExpressionFactory factory = new JavaParserSuspiciousExpressionFactory();

    // 状態フィールド
    private List<SuspiciousExpression> result;
    private List<SuspiciousReturnValue> resultCandidate;
    private SuspiciousReturnValue currentTarget;
    private ThreadReference targetThread;
    private MethodExitRequest activeMethodExitRequest;
    private StepRequest activeStepRequest;
    private MethodExitEvent recentMethodExitEvent;

    // StepIn/StepOut 状態管理
    private boolean steppingIn;        // true: StepIn 完了待ち, false: StepOut 完了待ち
    private int depthAtBreakpoint;     // ブレークポイント地点でのコールスタックの深さ
    @Override
    public List<SuspiciousExpression> search(SuspiciousExpression suspExpr) {
        // 状態の初期化
        this.currentTarget = (SuspiciousReturnValue) suspExpr;
        this.result = new ArrayList<>();
        this.resultCandidate = new ArrayList<>();
        this.targetThread = null;
        this.activeMethodExitRequest = null;
        this.activeStepRequest = null;
        this.recentMethodExitEvent = null;
        this.steppingIn = false;
        this.depthAtBreakpoint = 0;

        if (!currentTarget.hasMethodCalling()) return result;

        // Debugger生成
        EnhancedDebugger debugger = JDIDebugServerHandle.createSharedDebugger(currentTarget.failedTest);

        // ハンドラ登録
        debugger.registerEventHandler(BreakpointEvent.class,
                (vm, ev) -> handleBreakpoint(vm, (BreakpointEvent) ev));
        debugger.registerEventHandler(MethodExitEvent.class,
                (vm, ev) -> handleMethodExit(vm, (MethodExitEvent) ev));
        debugger.registerEventHandler(StepEvent.class,
                (vm, ev) -> handleStep(vm, (StepEvent) ev));

        // ブレークポイント設定と実行
        debugger.setBreakpoints(currentTarget.locateMethod.fullyQualifiedClassName(), List.of(currentTarget.locateLine));
        debugger.execute(() -> !result.isEmpty());

        if (result.isEmpty()) {
            logger.warn("戻り値の確認に失敗: actualValue={}, method={}, line={}",
                    currentTarget.actualValue, currentTarget.locateMethod, currentTarget.locateLine);
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
        recentMethodExitEvent = null;
        steppingIn = true;

        // ブレークポイント地点でのコールスタックの深さを取得
        depthAtBreakpoint = JDIUtils.getCallStackDepth(targetThread);

        // MethodExitRequest を作成（行の処理が完了するまで有効）
        activeMethodExitRequest = EnhancedDebugger.createMethodExitRequest(manager, targetThread);

        // メソッド呼び出しに入るための StepInRequest を作成
        activeStepRequest = EnhancedDebugger.createStepInRequest(manager, targetThread);
    }

    private void handleMethodExit(VirtualMachine vm, MethodExitEvent mee) {
        // 対象スレッドでない場合はスキップ
        if (!mee.thread().equals(targetThread)) return;

        int currentDepth = JDIUtils.getCallStackDepth(mee.thread());

        // 直前の MethodExitEvent を保持（検証用）
        recentMethodExitEvent = mee;

        if (currentDepth == depthAtBreakpoint + 1) {
            // 直接呼び出したメソッドの終了 → 戻り値を収集
            collectReturnValue(mee);
        } else if (currentDepth == depthAtBreakpoint) {
            // 親メソッドの終了 → return 文全体の戻り値で検証
            if (validateIsTargetExecution()) {
                result.addAll(resultCandidate);
            }
            // MethodExitRequest を無効化
            activeMethodExitRequest.disable();
            activeMethodExitRequest = null;
        }
        // 他の深さのイベントは無視
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
            // → 行の実行が終わった → 親メソッドの MethodExitEvent を待つ（handleMethodExit で処理）
            // StepRequest は不要（次の BreakpointEvent を待つ）
            return;
        }

        // 直接呼び出しのメソッドに入った → メソッドから抜けるための StepOutRequest を作成
        activeStepRequest = EnhancedDebugger.createStepOutRequest(manager, thread);
        steppingIn = false;
    }

    /**
     * StepOut 完了後の処理。呼び出し元（return 文の行）に戻った状態。
     * StepOut 完了時は必ず return 文の行にいるはず。
     */
    private void handleStepOutCompleted(EventRequestManager manager, ThreadReference thread, StepEvent se) {
        // 次のメソッド呼び出しを探す
        steppingIn = true;
        activeStepRequest = EnhancedDebugger.createStepInRequest(manager, thread);
    }

    /**
     * 呼び出し元が対象の位置（メソッドと行番号）かどうかを確認する。
     */
    private boolean isCalledFromTargetLocation(ThreadReference thread) {
        try {
            if (thread.frameCount() < 2) {
                return false;
            }
            StackFrame callerFrame = thread.frame(1);
            Location callerLocation = callerFrame.location();

            String callerClassName = callerLocation.declaringType().name();
            String callerMethodName = callerLocation.method().name();
            int callerLine = callerLocation.lineNumber();

            return callerClassName.equals(currentTarget.locateMethod.fullyQualifiedClassName())
                    && callerMethodName.equals(currentTarget.locateMethod.shortMethodName())
                    && callerLine == currentTarget.locateLine;
        } catch (IncompatibleThreadStateException e) {
            logger.error("呼び出し元の確認中にスレッドがサスペンド状態ではありません", e);
            return false;
        }
    }

    /**
     * 目的の実行かを検証する（return の戻り値で判定）。
     *
     * @return 目的の実行であれば true
     */
    private boolean validateIsTargetExecution() {
        return recentMethodExitEvent != null &&
                JDIUtils.getValueString(recentMethodExitEvent.returnValue()).equals(currentTarget.actualValue);
    }

    /**
     * MethodExitEvent から戻り値を収集し、resultCandidate に追加する。
     */
    private void collectReturnValue(MethodExitEvent mee) {
        MethodElementName invokedMethod = new MethodElementName(EnhancedDebugger.getFqmn(mee.method()));
        int locateLine = mee.location().lineNumber();
        String actualValue = JDIUtils.getValueString(mee.returnValue());
        try {
            SuspiciousReturnValue suspReturn = factory.createReturnValue(
                    currentTarget.failedTest,
                    invokedMethod,
                    locateLine,
                    actualValue
            );
            resultCandidate.add(suspReturn);
        } catch (RuntimeException e) {
            logger.debug("SuspiciousReturnValue の作成に失敗: {} (method={}, line={})",
                    e.getMessage(), invokedMethod, locateLine);
        }
    }
}
