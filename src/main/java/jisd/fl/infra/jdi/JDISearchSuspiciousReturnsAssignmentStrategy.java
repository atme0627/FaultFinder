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
import jisd.fl.infra.junit.JUnitDebugger;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class JDISearchSuspiciousReturnsAssignmentStrategy implements SearchSuspiciousReturnsStrategy {
    private final SuspiciousExpressionFactory factory = new JavaParserSuspiciousExpressionFactory();

    // 状態フィールド
    private List<SuspiciousExpression> result;
    private List<SuspiciousReturnValue> resultCandidate;
    private SuspiciousAssignment currentTarget;
    private int depthBeforeCall;
    private ThreadReference targetThread;
    private MethodExitRequest activeMethodExitRequest;
    private StepRequest activeStepRequest;

    @Override
    public List<SuspiciousExpression> search(SuspiciousExpression suspExpr) {
        // 状態の初期化
        this.currentTarget = (SuspiciousAssignment) suspExpr;
        this.result = new ArrayList<>();
        this.resultCandidate = new ArrayList<>();
        this.depthBeforeCall = 0;
        this.targetThread = null;
        this.activeMethodExitRequest = null;
        this.activeStepRequest = null;

        if (!currentTarget.hasMethodCalling()) return result;

        // Debugger生成
        JUnitDebugger debugger = new JUnitDebugger(currentTarget.failedTest);

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
            System.err.println("[[searchSuspiciousReturns]] Could not confirm [ "
                    + "(return value) == " + currentTarget.actualValue
                    + " ] on " + currentTarget.locateMethod + " line:" + currentTarget.locateLine);
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

        // ブレークポイント地点でのコールスタックの深さを取得
        depthBeforeCall = JDIUtils.getCallStackDepth(targetThread);

        // MethodExitRequest と StepOverRequest を作成
        activeMethodExitRequest = EnhancedDebugger.createMethodExitRequest(manager, targetThread);
        activeStepRequest = EnhancedDebugger.createStepOverRequest(manager, targetThread);
    }

    private void handleMethodExit(VirtualMachine vm, MethodExitEvent mee) {
        // 対象スレッドでない場合はスキップ
        if (!mee.thread().equals(targetThread)) return;

        // 直接呼び出したメソッドのみ収集
        if (JDIUtils.getCallStackDepth(mee.thread()) == depthBeforeCall + 1) {
            collectReturnValue(mee);
        }
    }

    private void handleStep(VirtualMachine vm, StepEvent se) {
        // リクエストを無効化
        activeMethodExitRequest.disable();
        activeStepRequest.disable();

        // 目的の実行かを検証
        if (validateIsTargetExecution(se, currentTarget.assignTarget)) {
            result.addAll(resultCandidate);
        }
        // 結果が空の場合は次の BreakpointEvent を待つ
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
            System.out.println("cannot create SuspiciousReturnValue: " + e.getMessage() + " at " + invokedMethod + " line:" + locateLine);
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
