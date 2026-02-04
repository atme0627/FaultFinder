package jisd.fl.infra.jdi;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.fl.core.domain.port.SearchSuspiciousReturnsStrategy;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.core.entity.susp.SuspiciousArgument;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.susp.SuspiciousReturnValue;
import jisd.fl.infra.javaparser.JavaParserSuspiciousExpressionFactory;
import jisd.fl.infra.jdi.testexec.JDIDebugServerHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class JDISearchSuspiciousReturnsArgumentStrategy implements SearchSuspiciousReturnsStrategy {
    private static final Logger logger = LoggerFactory.getLogger(JDISearchSuspiciousReturnsArgumentStrategy.class);
    private final SuspiciousExpressionFactory factory = new JavaParserSuspiciousExpressionFactory();

    // 状態フィールド
    private List<SuspiciousExpression> result;
    private List<SuspiciousReturnValue> resultCandidate;
    private SuspiciousArgument currentTarget;
    private ThreadReference targetThread;
    private MethodExitRequest activeMethodExitRequest;
    private StepRequest activeStepRequest;
    private MethodEntryRequest activeMethodEntryRequest;
    private int callCount;
    private boolean steppingIn;
    private int depthAtBreakpoint;

    // 引数式内で呼ばれたメソッドの戻り値を収集する。
    // targetReturnCallPositions で収集すべき直接呼び出しの番号を、
    // invokeCallCount で invoke メソッドの番号を指定し、callCount で判定する。
    @Override
    public List<SuspiciousExpression> search(SuspiciousExpression suspExpr) {
        // 状態の初期化
        this.currentTarget = (SuspiciousArgument) suspExpr;
        this.result = new ArrayList<>();
        this.resultCandidate = new ArrayList<>();
        this.targetThread = null;
        this.activeMethodExitRequest = null;
        this.activeStepRequest = null;
        this.activeMethodEntryRequest = null;
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
            logger.warn("引数の戻り値収集を確認できませんでした: actualValue={}, method={}, line={}",
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
        depthAtBreakpoint = JDIUtils.getCallStackDepth(targetThread);
        steppingIn = true;

        // リクエストを作成
        activeMethodExitRequest = EnhancedDebugger.createMethodExitRequest(manager, targetThread);
        activeStepRequest = EnhancedDebugger.createStepInRequest(manager, targetThread);
        activeMethodEntryRequest = EnhancedDebugger.createMethodEntryRequest(manager, targetThread);
    }

    private void handleMethodEntry(VirtualMachine vm, MethodEntryEvent mEntry) {
        // 対象スレッドでない場合はスキップ
        if (!mEntry.thread().equals(targetThread)) return;

        // 直接呼び出し（depth == depthAtBreakpoint + 1）のみ処理
        int currentDepth = JDIUtils.getCallStackDepth(mEntry.thread());
        if (currentDepth != depthAtBreakpoint + 1) return;

        // 直接呼び出しの回数をカウント（全メソッド対象）
        callCount++;

        // invokeCallCount 番目の直接呼び出しでのみ検証する
        if (callCount != currentTarget.invokeCallCount) return;

        // 暗黙メソッド呼び出しの検出: invokeCallCount に達したが invoke メソッド名が不一致
        if (!isInvokeMethod(mEntry.method())) {
            logger.warn("暗黙メソッド呼び出しの可能性: invokeCallCount={} で期待={} だが実際={}",
                    callCount, currentTarget.invokeMethodName.shortMethodName(), mEntry.method().name());
            disableRequests();
            return;
        }

        // invoke メソッドに入った → 引数の値で目的の実行かを検証
        if (JDIUtils.validateIsTargetExecutionArg(mEntry, currentTarget.actualValue(), currentTarget.argIndex)) {
            result.addAll(resultCandidate);
        }
        // 検証完了（成功・失敗とも）→ リクエストを無効化し、次の BreakpointEvent を待つ
        disableRequests();
    }

    private boolean isInvokeMethod(com.sun.jdi.Method method) {
        if (method.isConstructor()) {
            return method.declaringType().name()
                    .equals(currentTarget.invokeMethodName.fullyQualifiedClassName());
        } else {
            return method.name().equals(currentTarget.invokeMethodName.shortMethodName());
        }
    }

    private void handleMethodExit(VirtualMachine vm, MethodExitEvent mee) {
        // 対象スレッドでない場合はスキップ
        if (!mee.thread().equals(targetThread)) return;

        // 直接呼び出したメソッドの終了のみ処理（depth チェック）
        int currentDepth = JDIUtils.getCallStackDepth(mee.thread());
        if (currentDepth != depthAtBreakpoint + 1) return;

        // targetReturnCallPositions に含まれる直接呼び出しのみ収集
        if (currentTarget.targetReturnCallPositions.contains(callCount)) {
            collectReturnValue(mee);
        }
    }

    private void handleStep(VirtualMachine vm, StepEvent se) {
        // MethodEntryEvent で既に処理完了している場合はスキップ
        if (activeStepRequest == null) return;
        if (!se.thread().equals(targetThread)) return;

        EventRequestManager manager = vm.eventRequestManager();
        activeStepRequest.disable();
        activeStepRequest = null;

        if (steppingIn) {
            handleStepInCompleted(manager, se);
        } else {
            handleStepOutCompleted(manager, se);
        }
    }

    private void handleStepInCompleted(EventRequestManager manager, StepEvent se) {
        int currentDepth = JDIUtils.getCallStackDepth(se.thread());
        if (currentDepth > depthAtBreakpoint) {
            // メソッドに入った → StepOut で戻る
            steppingIn = false;
            activeStepRequest = EnhancedDebugger.createStepOutRequest(manager, se.thread());
        } else {
            // 行を離れた（メソッド呼び出しなし）→ 終了
            disableRequests();
        }
    }

    private void handleStepOutCompleted(EventRequestManager manager, StepEvent se) {
        // 行に戻った → 次の StepIn
        steppingIn = true;
        activeStepRequest = EnhancedDebugger.createStepInRequest(manager, se.thread());
    }

    /**
     * 動的に作成したリクエストを無効化する。
     */
    private void disableRequests() {
        if (activeMethodExitRequest != null) {
            activeMethodExitRequest.disable();
            activeMethodExitRequest = null;
        }
        if (activeStepRequest != null) {
            activeStepRequest.disable();
            activeStepRequest = null;
        }
        if (activeMethodEntryRequest != null) {
            activeMethodEntryRequest.disable();
            activeMethodEntryRequest = null;
        }
    }

    private void collectReturnValue(MethodExitEvent mee) {
        JDIUtils.createSuspiciousReturnValue(mee, currentTarget.failedTest(), factory)
                .ifPresent(resultCandidate::add);
    }
}