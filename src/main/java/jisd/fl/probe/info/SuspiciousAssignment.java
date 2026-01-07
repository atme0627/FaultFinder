package jisd.fl.probe.info;

import com.fasterxml.jackson.annotation.*;
import com.github.javaparser.ast.expr.*;
import com.sun.jdi.*;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.debug.EnhancedDebugger;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.probe.record.TracedValue;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.probe.record.TracedValuesAtLine;
import jisd.fl.util.TestUtil;
import jisd.fl.core.entity.MethodElementName;

import java.util.*;
import java.util.stream.Collectors;
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "failedTest", "locateMethod", "locateLine", "stmt", "expr", "actualValue", "children" })

public class SuspiciousAssignment extends SuspiciousExpression {

    //左辺で値が代入されている変数の情報
    @JsonIgnore
    private final SuspiciousVariable assignTarget;

    public SuspiciousAssignment(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, SuspiciousVariable assignTarget) {
        super(failedTest, locateMethod, locateLine, assignTarget.getActualValue());
        this.expr = ExtractExprAssignment.extractExprAssign(true, stmt);
        this.assignTarget = assignTarget;
    }

    @JsonCreator
    private SuspiciousAssignment(
            @JsonProperty("failedTest") String failedTest,
            @JsonProperty("locateMethod") String locateMethod,
            @JsonProperty("locateLine") int locateLine,
            @JsonProperty("actualValue") String actualValue,
            @JsonProperty("children") List<SuspiciousExpression> children
            ){
        super(failedTest, locateMethod, locateLine, actualValue, children);
        this.assignTarget = null;
        this.expr = ExtractExprAssignment.extractExprAssign(true, stmt);
    }

    @Override
    //TODO: 今はオブジェクトの違いを考慮していない
    public List<SuspiciousReturnValue> searchSuspiciousReturns() throws NoSuchElementException {
        final List<SuspiciousReturnValue> result = new ArrayList<>();
        if(!hasMethodCalling()) return result;

        //Debugger生成
        String main = TestUtil.getJVMMain(this.failedTest);
        String options = TestUtil.getJVMOption();
        EnhancedDebugger eDbg = new EnhancedDebugger(main, options);

        //対象の引数が属する行にたどり着いた時に行う処理を定義
        //ここではその行で呼ばれてるメソッド情報を抽出
        EnhancedDebugger.BreakpointHandler handler = (vm, bpe) -> {
            //既に情報が取得できている場合は終了
            if(!result.isEmpty()) return;

            List<SuspiciousReturnValue> resultCandidate = new ArrayList<>();
            EventRequestManager manager = vm.eventRequestManager();

            //このスレッドでの MethodExit を記録するリクエストを作成
            ThreadReference thread = bpe.thread();
            MethodExitRequest meReq = EnhancedDebugger.createMethodExitRequest(manager, thread);
            //この行の実行が終わったことを検知するステップリクエストを作成
            //この具象クラスではステップイベントの通知タイミングで、今調査していた行が調べたい行だったかを確認
            StepRequest stepReq = EnhancedDebugger.createStepOverRequest(manager, thread);

            // ブレークポイント地点でのコールスタックの深さを取得
            // 呼び出しメソッドの取得条件を 深さ == depthBeforeCall + 1　にすることで
            // 再帰呼び出し含め、その行で直接呼ばれたメソッドのみ取ってこれる
            int depthBeforeCall = TmpStaticUtils.getCallStackDepth(thread);

            //一旦 resume して、内部ループで MethodExit／Step を待つ
            vm.resume();
            boolean done = false;
            while (!done) {
                EventSet es = vm.eventQueue().remove();
                for (Event ev : es) {
                    //実行された、とあるメソッドから抜けた
                    if (ev instanceof MethodExitEvent) {
                        MethodExitEvent mee = (MethodExitEvent) ev;

                        //収集するのは指定した行で直接呼び出したメソッドのみ
                        //depthBeforeCallとコールスタックの深さを比較することで直接呼び出したメソッドかどうかを判定
                        if (mee.thread().equals(thread) && TmpStaticUtils.getCallStackDepth(mee.thread()) == depthBeforeCall + 1) {
                            MethodElementName invokedMethod = new MethodElementName(EnhancedDebugger.getFqmn(mee.method()));
                            int locateLine = mee.location().lineNumber();
                            String actualValue = TmpStaticUtils.getValueString(mee.returnValue());
                            try {
                                SuspiciousReturnValue suspReturn = new SuspiciousReturnValue(
                                        this.failedTest,
                                        invokedMethod,
                                        locateLine,
                                        actualValue
                                );
                                resultCandidate.add(suspReturn);
                            } catch (RuntimeException e) {
                                System.out.println("cannot create SuspiciousReturnValue: " + e.getMessage() + " at " + invokedMethod + " line:" + locateLine);
                            }
                        }
                        vm.resume();
                    }
                    //調査対象の行の実行(実行インスタンス)が終了
                    //ここで、調査した行が目的のものであったかチェック
                    if (ev instanceof StepEvent) {
                        done = true;
                        StepEvent se = (StepEvent) ev;
                        if(validateIsTargetExecution(se, this.getAssignTarget())) result.addAll(resultCandidate);
                        //vmをresumeしない
                    }
                }
            }
            //動的に作ったリクエストを無効化
            meReq.disable();
            stepReq.disable();
        };

        //VMを実行し情報を収集
        eDbg.handleAtBreakPoint(this.locateMethod.getFullyQualifiedClassName(), this.locateLine, handler);
        if(result.isEmpty()){
            System.err.println("[[searchSuspiciousReturns]] Could not confirm [ "
                    + "(return value) == " + this.actualValue
                    + " ] on " + this.locateMethod + " line:" + this.locateLine);
        }
        return result;
    }

    //この行の評価結果( = assignTargetへ代入された値)がactualValueと一致するか確認
    //TODO: 配列はとりあえず考えない
    static private boolean validateIsTargetExecution(StepEvent se, SuspiciousVariable assignTarget){
        try {
            if (!assignTarget.isPrimitive()) throw new RuntimeException("Reference type has not been supported yet.");
            if (assignTarget.isArray()) throw new RuntimeException("Array type has not been supported yet.");

            if (assignTarget.isField()) {
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
                Field field = refType.fieldByName(assignTarget.getSimpleVariableName());
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
                return evaluatedValue.equals(assignTarget.getActualValue());

            } else {
                //ローカル変数を取り出す
                StackFrame frame = se.thread().frame(0);
                List<LocalVariable> lvs = frame.visibleVariables();
                LocalVariable lvalue = lvs.stream().filter(lv -> lv.name().equals(assignTarget.getSimpleVariableName()))
                        .findFirst()
                        .orElseThrow();

                //評価結果を比較
                String evaluatedValue = TmpStaticUtils.getValueString(frame.getValue(lvalue));
                return evaluatedValue.equals(assignTarget.getActualValue());
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

    /**
     * exprから次に探索の対象となる変数の名前を取得する。
     * exprの演算に直接用いられている変数のみが対象で、引数やメソッド呼び出しの対象となる変数は除外する。
     * @return 変数名のリスト
     */
    protected List<String> extractNeighborVariableNames(boolean includeIndirectUsedVariable){
        return expr.findAll(NameExpr.class).stream()
                //引数やメソッド呼び出しに用いられる変数を除外
                .filter(nameExpr -> includeIndirectUsedVariable || nameExpr.findAncestor(MethodCallExpr.class).isEmpty())
                .map(NameExpr::toString)
                .collect(Collectors.toList());
    }

    public SuspiciousVariable getAssignTarget() {
        return assignTarget;
    }

    @Override
    public String toString(){
        return "[ SUSPICIOUS ASSIGNMENT ]\n" + "    " + locateMethod.methodSignature + "{\n       ...\n" + super.toString() + "\n       ...\n    }";
    }

    @Override
    protected TracedValueCollection traceAllValuesAtSuspExpr(int sleepTime){
        final List<TracedValue> result = new ArrayList<>();

        //Debugger生成
        String main = TestUtil.getJVMMain(this.failedTest);
        String options = TestUtil.getJVMOption();
        EnhancedDebugger eDbg = new EnhancedDebugger(main, options);

        //対象の引数が属する行にたどり着いた時に行う処理を定義
        //ここではその行で呼ばれてるメソッド情報を抽出
        EnhancedDebugger.BreakpointHandler handler = (vm, bpe) -> {
            //既に情報が取得できている場合は終了
            if(!result.isEmpty()) return;

            EventRequestManager manager = vm.eventRequestManager();

            //この行の実行が終わったことを検知するステップリクエストを作成
            //この具象クラスではステップイベントの通知タイミングで、今調査していた行が調べたい行だったかを確認
            ThreadReference thread = bpe.thread();
            StepRequest stepReq = EnhancedDebugger.createStepOverRequest(manager, thread);

            //周辺の値を観測
            List<TracedValue> resultCandidate;
            try {
                resultCandidate = watchAllVariablesInLine(thread.frame(0));
            } catch (IncompatibleThreadStateException e) {
                throw new RuntimeException(e);
            }


            //resume してステップイベントを待つ
            vm.resume();
            boolean done = false;
            while (!done) {
                EventSet es = vm.eventQueue().remove();
                for (Event ev : es) {
                    //調査対象の行の実行(実行インスタンス)が終了
                    //ここで、調査した行が目的のものであったかチェック
                    if (ev instanceof StepEvent) {
                        done = true;
                        StepEvent se = (StepEvent) ev;
                        if(validateIsTargetExecution(se, this.getAssignTarget())) result.addAll(resultCandidate);
                        //vmをresumeしない
                    }
                }
            }
            //動的に作ったリクエストを無効化
            stepReq.disable();
        };

        //VMを実行し情報を収集
        eDbg.handleAtBreakPoint(this.locateMethod.getFullyQualifiedClassName(), this.locateLine, handler);
        return TracedValuesAtLine.of(result);
    }
}
