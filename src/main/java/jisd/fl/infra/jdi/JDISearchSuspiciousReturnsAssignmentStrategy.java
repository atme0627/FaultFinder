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
import jisd.fl.core.entity.susp.*;
import jisd.fl.infra.javaparser.JavaParserSuspiciousExpressionFactory;
import jisd.fl.infra.jdi.testexec.JDIDebugServerHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class JDISearchSuspiciousReturnsAssignmentStrategy implements SearchSuspiciousReturnsStrategy {
    private static final Logger logger = LoggerFactory.getLogger(JDISearchSuspiciousReturnsAssignmentStrategy.class);
    private final SuspiciousExpressionFactory factory = new JavaParserSuspiciousExpressionFactory();

    // 状態フィールド
    private List<SuspiciousExpression> result;
    private List<SuspiciousReturnValue> resultCandidate;
    private SuspiciousAssignment currentTarget;
    private ThreadReference targetThread;
    private MethodExitRequest activeMethodExitRequest;
    private StepRequest activeStepRequest;

    // StepIn/StepOut 状態管理
    private boolean steppingIn;        // true: StepIn 完了待ち, false: StepOut 完了待ち
    private boolean collectingReturn;  // true: MethodExitEvent 待ち
    private int enteredMethodDepth;    // 入ったメソッドの深さ

    @Override
    public List<SuspiciousExpression> search(SuspiciousExpression suspExpr) {
        // 状態の初期化
        this.currentTarget = (SuspiciousAssignment) suspExpr;
        this.result = new ArrayList<>();
        this.resultCandidate = new ArrayList<>();
        this.targetThread = null;
        this.activeMethodExitRequest = null;
        this.activeStepRequest = null;
        this.steppingIn = false;
        this.collectingReturn = false;
        this.enteredMethodDepth = 0;

        if (!currentTarget.hasMethodCalling()) return result;

        // Debugger生成
        EnhancedDebugger debugger = JDIDebugServerHandle.createSharedDebugger(currentTarget.failedTest());

        // ハンドラ登録
        debugger.registerEventHandler(BreakpointEvent.class,
                (vm, ev) -> handleBreakpoint(vm, (BreakpointEvent) ev));
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
        steppingIn = true;
        collectingReturn = false;

        // メソッド呼び出しに入るための StepInRequest を作成
        activeStepRequest = EnhancedDebugger.createStepInRequest(manager, targetThread);
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
            if (validateIsTargetExecution(se, currentTarget.assignTarget)) {
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
            if (validateIsTargetExecution(se, currentTarget.assignTarget)) {
                result.addAll(resultCandidate);
            }
            // 結果が空の場合は次の BreakpointEvent を待つ
        }
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

            return callerClassName.equals(currentTarget.locateMethod().fullyQualifiedClassName())
                    && callerMethodName.equals(currentTarget.locateMethod().shortMethodName())
                    && callerLine == currentTarget.locateLine();
        } catch (IncompatibleThreadStateException e) {
            logger.error("呼び出し元の確認中にスレッドがサスペンド状態ではありません", e);
            return false;
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
                    currentTarget.failedTest(),
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

    /**
     * 代入先変数の現在値が actualValue と一致するか検証する。
     * 一致すれば目的の実行であると判断する。
     *
     * @return 目的の実行であれば true
     */
    static boolean validateIsTargetExecution(StepEvent se, SuspiciousVariable assignTarget) {
        // TODO: 配列はとりあえず考えない
        if (!assignTarget.isPrimitive()) {
            throw new RuntimeException("参照型はまだサポートされていません: " + assignTarget.variableName());
        }
        if (assignTarget.isArray()) {
            throw new RuntimeException("配列型はまだサポートされていません: " + assignTarget.variableName());
        }

        try {
            StackFrame frame = se.thread().frame(0);
            String evaluatedValue = (assignTarget instanceof SuspiciousFieldVariable)
                    ? getFieldValue(frame, assignTarget.variableName())
                    : getLocalVariableValue(frame, assignTarget.variableName());
            return evaluatedValue.equals(assignTarget.actualValue());
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException("対象スレッドが中断状態ではありません", e);
        } catch (AbsentInformationException e) {
            throw new RuntimeException("デバッグ情報が不足しています（-g オプションでコンパイルされていない可能性）", e);
        } catch (NoSuchElementException e) {
            // 変数が存在しない → 目的の実行ではない
            return false;
        }
    }

    /**
     * フィールド変数の値を取得する。
     */
    private static String getFieldValue(StackFrame frame, String fieldName) {
        ObjectReference thisObject = frame.thisObject();
        ReferenceType refType = (thisObject != null)
                ? thisObject.referenceType()
                : frame.location().declaringType();

        Field field = refType.fieldByName(fieldName);
        if (field == null) {
            throw new NoSuchElementException("フィールドが見つかりません: " + fieldName);
        }

        if (field.isStatic()) {
            return refType.getValue(field).toString();
        } else {
            if (thisObject == null) {
                throw new RuntimeException(
                        "インスタンスフィールドの取得にはthisオブジェクトが必要ですが、nullでした: " + fieldName);
            }
            return thisObject.getValue(field).toString();
        }
    }

    /**
     * ローカル変数の値を取得する。
     */
    private static String getLocalVariableValue(StackFrame frame, String variableName)
            throws AbsentInformationException {
        LocalVariable localVar = frame.visibleVariables().stream()
                .filter(lv -> lv.name().equals(variableName))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("ローカル変数が見つかりません: " + variableName));
        return JDIUtils.getValueString(frame.getValue(localVar));
    }
}
