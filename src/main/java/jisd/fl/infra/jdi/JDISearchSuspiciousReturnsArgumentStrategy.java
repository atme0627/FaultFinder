package jisd.fl.infra.jdi;

import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.Method;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.fl.core.domain.port.SearchSuspiciousReturnsStrategy;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousArgument;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.susp.SuspiciousReturnValue;
import jisd.fl.infra.javaparser.JavaParserSuspiciousExpressionFactory;
import jisd.fl.infra.junit.JUnitDebugger;
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

    //引数のindexを指定してその引数の評価の直前でsuspendするのは激ムズなのでやらない
    //引数を区別せず、引数の評価の際に呼ばれたすべてのメソッドについて情報を取得し
    //Expressionを静的解析してexpressionで直接呼ばれてるメソッドのみに絞る
    //ex.) expressionがx.f(y.g())の時、fのみとる。y.g()はfの探索の後行われるはず
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
        JUnitDebugger debugger = new JUnitDebugger(currentTarget.failedTest);

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
        debugger.setBreakpoints(currentTarget.locateMethod.fullyQualifiedClassName(), List.of(currentTarget.locateLine));
        debugger.execute(() -> !result.isEmpty());

        if (result.isEmpty()) {
            logger.warn("引数の戻り値収集を確認できませんでした: actualValue={}, method={}, line={}",
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
        callCount = 0;

        // リクエストを作成
        activeMethodExitRequest = EnhancedDebugger.createMethodExitRequest(manager, targetThread);
        activeStepRequest = EnhancedDebugger.createStepOverRequest(manager, targetThread);
        activeMethodEntryRequest = EnhancedDebugger.createMethodEntryRequest(manager, targetThread);
    }

    private void handleMethodEntry(VirtualMachine vm, MethodEntryEvent mEntry) {
        // 対象スレッドでない場合はスキップ
        if (!mEntry.thread().equals(targetThread)) return;

        // callee メソッドかを確認
        Method method = mEntry.method();
        boolean isTarget;
        if (method.isConstructor()) {
            // calleeMethodName には FullyQualifiedClassName を保持している想定
            isTarget = method.declaringType().name()
                    .equals(currentTarget.calleeMethodName.fullyQualifiedClassName());
        } else {
            isTarget = method.name().equals(currentTarget.calleeMethodName.shortMethodName());
        }

        if (!isTarget) return;

        // callee メソッドに入った → 引数の値で目的の実行かを検証
        if (JDIUtils.validateIsTargetExecutionArg(mEntry, currentTarget.actualValue, currentTarget.argIndex)) {
            result.addAll(resultCandidate);
        }
        // 検証完了（成功・失敗とも）→ リクエストを無効化し、次の BreakpointEvent を待つ
        disableRequests();
    }

    private void handleMethodExit(VirtualMachine vm, MethodExitEvent mee) {
        // 対象スレッドでない場合はスキップ
        if (!mee.thread().equals(targetThread)) return;

        callCount++;

        // 収集するのは指定した行で直接呼び出したメソッドのみ
        // caller のメソッドが breakpoint のメソッド（locateMethod）と一致するか確認
        try {
            StackFrame caller = mee.thread().frame(1);
            String callerMethodName = caller.location().method().name();
            if (!callerMethodName.equals(currentTarget.locateMethod.shortMethodName())) {
                return;
            }
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException("Target thread must be suspended.");
        }

        // targetCallCount に達していない場合はスキップ
        if (callCount < currentTarget.targetCallCount) return;

        // targetMethod のみ収集
        if (currentTarget.targetMethodNames().contains(mee.method().name())) {
            collectReturnValue(mee);
        }
    }

    private void handleStep(VirtualMachine vm, StepEvent se) {
        // 行の実行が終了 → リクエストを無効化し、次の BreakpointEvent を待つ
        disableRequests();
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
            logger.debug("SuspiciousReturnValue を作成できませんでした: {} (method={}, line={})",
                    e.getMessage(), invokedMethod, locateLine);
        }
    }
}