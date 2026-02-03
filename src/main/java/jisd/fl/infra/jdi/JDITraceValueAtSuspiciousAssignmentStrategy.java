package jisd.fl.infra.jdi;

import com.sun.jdi.*;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;
import jisd.fl.core.domain.port.TraceValueAtSuspiciousExpressionStrategy;
import jisd.fl.core.entity.susp.*;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.infra.jdi.testexec.JDIDebugServerHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class JDITraceValueAtSuspiciousAssignmentStrategy implements TraceValueAtSuspiciousExpressionStrategy {
    //TODO: 今はオブジェクトの違いを考慮していない

    // 状態フィールド
    private List<TracedValue> result;
    private List<TracedValue> resultCandidate;
    private SuspiciousAssignment currentTarget;
    private StepRequest activeStepRequest;

    public List<TracedValue> traceAllValuesAtSuspExpr(SuspiciousExpression suspExpr){
        // 状態の初期化
        this.currentTarget = (SuspiciousAssignment) suspExpr;
        this.result = new ArrayList<>();
        this.resultCandidate = null;
        this.activeStepRequest = null;

        //Debugger生成
        EnhancedDebugger debugger = JDIDebugServerHandle.createSharedDebugger(currentTarget.failedTest());

        // ハンドラ登録
        debugger.registerEventHandler(BreakpointEvent.class,
                (vm, ev) -> handleBreakpoint(vm, (BreakpointEvent) ev));
        debugger.registerEventHandler(StepEvent.class,
                (vm, ev) -> handleStep(vm, (StepEvent) ev));

        // ブレークポイント設定
        debugger.setBreakpoints(
                currentTarget.locateMethod().fullyQualifiedClassName(),
                List.of(currentTarget.locateLine())
        );

        // 実行（result が取得できたら終了）
        debugger.execute(() -> !result.isEmpty());

        return result;
    }

    private void handleBreakpoint(VirtualMachine vm, BreakpointEvent bpe) {
        //既に情報が取得できている場合は終了
        if (!result.isEmpty()) return;

        EventRequestManager manager = vm.eventRequestManager();

        //この行の実行が終わったことを検知するステップリクエストを作成
        ThreadReference thread = bpe.thread();
        activeStepRequest = EnhancedDebugger.createStepOverRequest(manager, thread);

        //周辺の値を観測
        try {
            StackFrame frame = thread.frame(0);
            resultCandidate = JDIUtils.watchAllVariablesInLine(frame, currentTarget.locateLine());
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException(e);
        }
        // StepEvent は execute() のイベントループで処理される
    }

    private void handleStep(VirtualMachine vm, StepEvent se) {
        //調査対象の行の実行(実行インスタンス)が終了
        //ここで、調査した行が目的のものであったかチェック
        if (validateIsTargetExecution(se, currentTarget.assignTarget)) {
            result.addAll(resultCandidate);
        }

        //動的に作ったリクエストを無効化
        if (activeStepRequest != null) {
            activeStepRequest.disable();
            activeStepRequest = null;
        }
    }

    /**
     * 代入後の値が actualValue と一致するか確認する。
     * 一致すれば、この実行が目的の代入であると判定する。
     */
    static boolean validateIsTargetExecution(StepEvent se, SuspiciousVariable assignTarget) {
        if (!assignTarget.isPrimitive()) {
            throw new RuntimeException("Reference type has not been supported yet.");
        }
        if (assignTarget.isArray()) {
            throw new RuntimeException("Array type has not been supported yet.");
        }

        try {
            StackFrame frame = se.thread().frame(0);
            String evaluatedValue = getAssignedValue(frame, assignTarget);
            return evaluatedValue.equals(assignTarget.actualValue());
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException("Target thread must be suspended.", e);
        } catch (AbsentInformationException e) {
            throw new RuntimeException("Debug information is not available.", e);
        } catch (NoSuchElementException e) {
            // 変数が存在しない → 目的の実行ではない
            return false;
        }
    }

    /**
     * 代入後の変数の値を取得する。
     */
    private static String getAssignedValue(StackFrame frame, SuspiciousVariable assignTarget)
            throws AbsentInformationException {
        if (assignTarget instanceof SuspiciousFieldVariable) {
            return getFieldValue(frame, assignTarget.variableName());
        } else {
            return getLocalVariableValue(frame, assignTarget.variableName());
        }
    }

    /**
     * フィールドの値を取得する。
     */
    private static String getFieldValue(StackFrame frame, String fieldName) {
        ObjectReference targetObject = frame.thisObject();
        ReferenceType refType = (targetObject != null)
                ? targetObject.referenceType()
                : frame.location().declaringType();

        Field field = refType.fieldByName(fieldName);
        if (field == null) {
            throw new NoSuchElementException("Field not found: " + fieldName);
        }

        Value value;
        if (field.isStatic()) {
            value = refType.getValue(field);
        } else {
            if (targetObject == null) {
                throw new IllegalStateException("Cannot access instance field from static context: " + fieldName);
            }
            value = targetObject.getValue(field);
        }

        return JDIUtils.getValueString(value);
    }

    /**
     * ローカル変数の値を取得する。
     */
    private static String getLocalVariableValue(StackFrame frame, String variableName)
            throws AbsentInformationException {
        LocalVariable lvalue = frame.visibleVariables().stream()
                .filter(lv -> lv.name().equals(variableName))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Local variable not found: " + variableName));

        return JDIUtils.getValueString(frame.getValue(lvalue));
    }
}
