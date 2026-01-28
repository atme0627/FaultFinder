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
