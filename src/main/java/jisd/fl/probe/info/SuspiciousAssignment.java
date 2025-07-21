package jisd.fl.probe.info;

import com.fasterxml.jackson.annotation.*;
import com.github.javaparser.ast.body.VariableDeclarator;
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
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.MethodElementName;

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
        this.expr = extractExpr();
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
        this.expr = extractExpr();
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
            int depthBeforeCall = getCallStackDepth(thread);

            //一旦 resume して、内部ループで MethodExit／Step を待つ
            vm.resume();
            boolean done = false;
            while (!done) {
                EventSet es = vm.eventQueue().remove();
                for (Event ev : es) {
                    //実行された、とあるメソッドから抜けた
                    if (ev instanceof MethodExitEvent) {
                        MethodExitEvent mee = (MethodExitEvent) ev;
                        StackFrame caller = null;
                        try {
                            //thread()がsuspendされていないと例外を投げる
                            //普通は成功するはず
                            //waitForThreadPreparation(mee.thread());
                            caller = mee.thread().frame(1);
                        } catch (IncompatibleThreadStateException e) {
                            throw new RuntimeException("Target thread must be suspended.");
                        }

                        //収集するのは指定した行で直接呼び出したメソッドのみ
                        //depthBeforeCallとコールスタックの深さを比較することで直接呼び出したメソッドかどうかを判定
                        if (mee.thread().equals(thread) && getCallStackDepth(mee.thread()) == depthBeforeCall + 1) {
                            MethodElementName invokedMethod = new MethodElementName(EnhancedDebugger.getFqmn(mee.method()));
                            int locateLine = mee.location().lineNumber();
                            String actualValue = mee.returnValue().toString();
                            SuspiciousReturnValue suspReturn = new SuspiciousReturnValue(
                                    this.failedTest,
                                    invokedMethod,
                                    locateLine,
                                    actualValue
                            );
                            resultCandidate.add(suspReturn);
                        }
                        vm.resume();
                    }
                    //調査対象の行の実行が終了
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
            throw new NoSuchElementException("Could not confirm [ "
                    + getAssignTarget().getSimpleVariableName() + " == " + getAssignTarget().getActualValue()
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
                //waitForThreadPreparation(se.thread());
                StackFrame frame = se.thread().frame(0);
                List<LocalVariable> lvs = frame.visibleVariables();
                LocalVariable lvalue = lvs.stream().filter(lv -> lv.name().equals(assignTarget.getSimpleVariableName()))
                        .findFirst()
                        .orElseThrow();

                //評価結果を比較
                String evaluatedValue = frame.getValue(lvalue).toString();
                return evaluatedValue.equals(assignTarget.getActualValue());
            }
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException("Target thread must be suspended.");
        } catch (AbsentInformationException e){
            throw new RuntimeException("Something is wrong.");
        } catch (NoSuchElementException e){
            throw new RuntimeException("Variable [" + assignTarget.getSimpleVariableName() +
                    "] is not found in " + assignTarget.getLocateMethodElement());
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

    @Override
    protected Expression extractExpr() {
        return extractExpr(true);
    }
    protected Expression extractExpr(boolean deleteParentNode) {
        try {
            Expression result = null;
            Optional<AssignExpr> assignExpr = stmt.findFirst(AssignExpr.class);
            if(assignExpr.isPresent()) {
                if(assignExpr.get().getOperator() == AssignExpr.Operator.ASSIGN) {
                    result = assignExpr.get().getValue();
                }
                else {
                    result = assignExpr.get();
                }
            }
            else {
                VariableDeclarationExpr vdExpr = stmt.findFirst(VariableDeclarationExpr.class).orElseThrow();
                //代入文がひとつであると仮定
                VariableDeclarator var = vdExpr.getVariable(0);
                result = var.getInitializer().orElseThrow();
            }

            if(!deleteParentNode) return result;
            result = result.clone();
            //親ノードの情報を消す
            result.setParentNode(null);
            return result;

        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract expression from [" + locateMethod + ":" + locateLine + "].");
        }
    }

    static private void waitForThreadPreparation(ThreadReference thread){
        try {
            while(!thread.isSuspended()){
                    Thread.sleep(10);
            }
        } catch (InterruptedException ignored) {
        }
    }

    public SuspiciousVariable getAssignTarget() {
        return assignTarget;
    }

    @Override
    public String toString(){
        return "[ SUSPICIOUS ASSIGNMENT ]\n" + "    " + locateMethod.methodSignature + "{\n       ...\n" + super.toString() + "\n       ...\n    }";
    }
}
