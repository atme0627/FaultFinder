package jisd.fl.infra.jdi;

import com.sun.jdi.*;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;
import jisd.fl.core.domain.port.TraceValueAtSuspiciousExpressionStrategy;
import jisd.fl.core.entity.susp.*;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.infra.junit.JUnitDebugger;

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
        JUnitDebugger debugger = new JUnitDebugger(currentTarget.failedTest);

        // ハンドラ登録
        debugger.registerEventHandler(BreakpointEvent.class,
                (vm, ev) -> handleBreakpoint(vm, (BreakpointEvent) ev));
        debugger.registerEventHandler(StepEvent.class,
                (vm, ev) -> handleStep(vm, (StepEvent) ev));

        // ブレークポイント設定
        debugger.setBreakpoints(
                currentTarget.locateMethod.fullyQualifiedClassName(),
                List.of(currentTarget.locateLine)
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
            resultCandidate = JDIUtils.watchAllVariablesInLine(frame, currentTarget.locateLine);
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

    //この行の評価結果( = assignTargetへ代入された値)がactualValueと一致するか確認
    //TODO: 配列はとりあえず考えない
    static boolean validateIsTargetExecution(StepEvent se, SuspiciousVariable assignTarget){
        try {
            if (!assignTarget.isPrimitive()) throw new RuntimeException("Reference type has not been supported yet.");
            if (assignTarget.isArray()) throw new RuntimeException("Array type has not been supported yet.");

            if (assignTarget instanceof SuspiciousFieldVariable) {
                //フィールドを持つクラスの型情報を取得
                ReferenceType refType;
                StackFrame frame = se.thread().frame(0);
                ObjectReference targetObject = frame.thisObject();
                if (targetObject != null) {
                    refType = targetObject.referenceType();
                } else {
                    refType = frame.location().declaringType();
                }

                //フィールド情報を取得
                Field field = refType.fieldByName(assignTarget.variableName());
                if(field == null) throw new NoSuchElementException();

                //評価結果を比較
                String evaluatedValue;
                if(field.isStatic()){
                    evaluatedValue = refType.getValue(field).toString();
                }
                else {
                    if(targetObject == null) throw new RuntimeException("Something is wrong.");
                    evaluatedValue = targetObject.getValue(field).toString();
                }
                return evaluatedValue.equals(assignTarget.actualValue());

            } else {
                //ローカル変数を取り出す
                StackFrame frame = se.thread().frame(0);
                List<LocalVariable> lvs = frame.visibleVariables();
                LocalVariable lvalue = lvs.stream().filter(lv -> lv.name().equals(assignTarget.variableName()))
                        .findFirst()
                        .orElseThrow();

                //評価結果を比較
                String evaluatedValue = JDIUtils.getValueString(frame.getValue(lvalue));
                return evaluatedValue.equals(assignTarget.actualValue());
            }
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException("Target thread must be suspended.");
        } catch (AbsentInformationException e){
            throw new RuntimeException("Something is wrong.");
        } catch (NoSuchElementException e){
            //値がそもそも存在しない --> 目的の実行ではない
            return false;
        }
    }
}
