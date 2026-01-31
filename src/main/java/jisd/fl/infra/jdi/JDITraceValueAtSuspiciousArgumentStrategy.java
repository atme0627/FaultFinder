package jisd.fl.infra.jdi;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;
import jisd.fl.core.domain.port.TraceValueAtSuspiciousExpressionStrategy;
import jisd.fl.core.entity.susp.SuspiciousArgument;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.infra.junit.JUnitDebugger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class JDITraceValueAtSuspiciousArgumentStrategy implements TraceValueAtSuspiciousExpressionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(JDITraceValueAtSuspiciousArgumentStrategy.class);

    // 状態フィールド
    private List<TracedValue> result;
    private List<TracedValue> resultCandidate;
    private SuspiciousArgument currentTarget;
    private StepRequest activeStepRequest;
    private boolean steppingOut;  // true: StepOut中, false: StepIn中

    public List<TracedValue> traceAllValuesAtSuspExpr(SuspiciousExpression suspExpr){
        // 状態の初期化
        this.currentTarget = (SuspiciousArgument) suspExpr;
        this.result = new ArrayList<>();
        this.resultCandidate = null;
        this.activeStepRequest = null;
        this.steppingOut = false;

        // Debugger生成
        JUnitDebugger debugger = new JUnitDebugger(currentTarget.failedTest);

        // ハンドラ登録
        debugger.registerEventHandler(BreakpointEvent.class,
                (vm, ev) -> handleBreakpoint(vm, (BreakpointEvent) ev));
        debugger.registerEventHandler(StepEvent.class,
                (vm, ev) -> handleStep(vm, (StepEvent) ev));

        // ブレークポイント設定と実行
        debugger.setBreakpoints(currentTarget.locateMethod.fullyQualifiedClassName(), List.of(currentTarget.locateLine));
        debugger.execute(() -> !result.isEmpty());
        return result;
    }

    private void handleBreakpoint(VirtualMachine vm, BreakpointEvent bpe) {
        // 既に情報が取得できている場合は終了
        if (!result.isEmpty()) return;

        // 検索状態をリセット
        this.steppingOut = false;

        EventRequestManager manager = vm.eventRequestManager();
        ThreadReference thread = bpe.thread();

        // 周辺の値を観測
        try {
            StackFrame frame = thread.frame(0);
            resultCandidate = JDIUtils.watchAllVariablesInLine(frame, currentTarget.locateLine);
        } catch (IncompatibleThreadStateException e) {
            String msg = String.format("スレッドがサスペンド状態ではありません。対象=%s:%d",
                    currentTarget.locateMethod, currentTarget.locateLine);
            logger.error(msg, e);
            throw new IllegalStateException(msg, e);
        }

        // メソッド呼び出しに入るための StepInRequest を作成
        activeStepRequest = EnhancedDebugger.createStepInRequest(manager, thread);
        // execute() のループが resume を処理
    }

    private void handleStep(VirtualMachine vm, StepEvent se) {
        // 既に結果が取得できている場合はスキップ
        if (!result.isEmpty()) return;

        EventRequestManager manager = vm.eventRequestManager();
        ThreadReference thread = se.thread();

        // 現在の StepRequest を削除（同一スレッドに複数の StepRequest は作成できない）
        manager.deleteEventRequest(activeStepRequest);
        activeStepRequest = null;

        if (steppingOut) {
            handleStepOutCompleted(manager, thread, se.location().lineNumber());
        } else {
            handleStepInCompleted(manager, thread, se.location().method());
        }
    }

    /**
     * StepOut 完了後の処理。呼び出し元に戻った状態。
     */
    private void handleStepOutCompleted(EventRequestManager manager, ThreadReference thread, int currentLine) {
        if (currentLine == currentTarget.locateLine) {
            // まだ同じ行にいる → 次のメソッド呼び出しを探す
            steppingOut = false;
            activeStepRequest = EnhancedDebugger.createStepInRequest(manager, thread);
        }
        // 行を離れた場合は次の BreakpointEvent を待つ
    }

    /**
     * StepIn 完了後の処理。何らかのメソッドに入った状態。
     */
    private void handleStepInCompleted(EventRequestManager manager, ThreadReference thread, Method method) {
        // 呼び出し元の位置を確認（メソッドに入った場合は frame(1) が呼び出し元）
        if (!isCalledFromTargetLocation(thread)) {
            // メソッドに入っていない、または対象位置からの呼び出しでない → 次の BreakpointEvent を待つ
            return;
        }

        // 対象メソッドか確認
        boolean isTarget = isTargetMethod(method);

        if (isTarget && validateArgumentValue(thread)) {
            // 対象メソッドで引数も一致 → 完了
            result.addAll(resultCandidate);
        } else {
            // 対象メソッドでない、または引数が不一致 → StepOut して次のメソッド呼び出しを探す
            steppingOut = true;
            activeStepRequest = EnhancedDebugger.createStepOutRequest(manager, thread);
        }
    }

    /**
     * 指定されたメソッドが対象の callee メソッドかどうかを確認する。
     */
    private boolean isTargetMethod(Method method) {
        if (method.isConstructor()) {
            return method.declaringType().name()
                    .equals(currentTarget.invokeMethodName.fullyQualifiedClassName());
        } else {
            return method.name().equals(currentTarget.invokeMethodName.shortMethodName());
        }
    }

    private boolean validateArgumentValue(ThreadReference thread) {
        try {
            List<Value> args = thread.frame(0).getArgumentValues();
            int argIndex = currentTarget.argIndex;
            return args.size() > argIndex &&
                    JDIUtils.getValueString(args.get(argIndex)).equals(currentTarget.actualValue);
        } catch (IncompatibleThreadStateException e) {
            String msg = String.format("引数の検証中にスレッドがサスペンド状態ではありません。対象メソッド=%s, argIndex=%d",
                    currentTarget.invokeMethodName, currentTarget.argIndex);
            logger.error(msg, e);
            throw new IllegalStateException(msg, e);
        }
    }

    /**
     * 呼び出し元が対象の位置（メソッドと行番号）かどうかを確認する。
     * @return 対象位置からの呼び出しの場合 true
     */
    private boolean isCalledFromTargetLocation(ThreadReference thread) {
        try {
            if (thread.frameCount() < 2) {
                return false;
            }
            StackFrame callerFrame = thread.frame(1);
            Location callerLocation = callerFrame.location();

            // 呼び出し元のメソッドと行番号を確認
            String callerClassName = callerLocation.declaringType().name();
            String callerMethodName = callerLocation.method().name();
            int callerLine = callerLocation.lineNumber();

            return callerClassName.equals(currentTarget.locateMethod.fullyQualifiedClassName())
                    && callerMethodName.equals(currentTarget.locateMethod.shortMethodName())
                    && callerLine == currentTarget.locateLine;
        } catch (IncompatibleThreadStateException e) {
            String msg = String.format("呼び出し元の確認中にスレッドがサスペンド状態ではありません。対象=%s:%d",
                    currentTarget.locateMethod, currentTarget.locateLine);
            logger.error(msg, e);
            throw new IllegalStateException(msg, e);
        }
    }
}
