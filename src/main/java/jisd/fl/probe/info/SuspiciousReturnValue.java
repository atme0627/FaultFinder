package jisd.fl.probe.info;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.github.javaparser.ast.expr.Expression;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.debug.EnhancedDebugger;
import jisd.fl.probe.record.TracedValue;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.probe.record.TracedValuesAtLine;
import jisd.fl.util.TestUtil;
import jisd.fl.core.entity.MethodElementName;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "failedTest", "locateMethod", "locateLine", "stmt", "expr", "actualValue", "children" })

public class SuspiciousReturnValue extends SuspiciousExpression {
    public SuspiciousReturnValue(MethodElementName failedTest, MethodElementName locateMethod, int locateLine, String actualValue) {
        super(failedTest, locateMethod, locateLine, actualValue);
        this.expr = extractExpr();
    }

    @JsonCreator
    private SuspiciousReturnValue(
            @JsonProperty("failedTest") String failedTest,
            @JsonProperty("locateMethod") String locateMethod,
            @JsonProperty("locateLine") int locateLine,
            @JsonProperty("actualValue") String actualValue,
            @JsonProperty("children") List<SuspiciousExpression> children
    ){
        super(failedTest, locateMethod, locateLine, actualValue, children);
        this.expr = extractExpr();
    }
    
    @Override
    public List<SuspiciousReturnValue> searchSuspiciousReturns() throws NoSuchElementException {
        final List<SuspiciousReturnValue> result = new ArrayList<>();
        if(!hasMethodCalling()) return result;

        //Debugger生成
        String main = TestUtil.getJVMMain(this.failedTest);
        String options = TestUtil.getJVMOption();
        EnhancedDebugger eDbg = new EnhancedDebugger(main, options);
        //ブレークポイントにヒットした時に行う処理を定義
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
            StepRequest stepReq = EnhancedDebugger.createStepOutRequest(manager, thread);

            //直前に通知されたMethodExitEventを保持
            //StepEventでreturnから返った時にこのMEEを使ってreturnのactualValueを手にいれる。
            MethodExitEvent recentMee = null;

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
                        recentMee = mee;
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
                            String actualValue = getValueString(mee.returnValue());
                            try {
                                SuspiciousReturnValue suspReturn = new SuspiciousReturnValue(
                                        this.failedTest,
                                        invokedMethod,
                                        locateLine,
                                        actualValue
                                );
                                resultCandidate.add(suspReturn);
                            }
                            catch (RuntimeException e){
                                System.out.println("cannot create SuspiciousReturnValue: " + e.getMessage() + " at " + invokedMethod + " line:" + locateLine);
                            }
                        }
                        vm.resume();
                    }
                    //調査対象の行の実行が終了
                    //ここで、調査した行が目的のものであったかチェック
                    if (ev instanceof StepEvent) {
                        done = true;
                        StepEvent se = (StepEvent) ev;
                        if(recentMee == null){
                            //ここには到達しないはず
                            throw new RuntimeException("Something is wrong.");
                        }
                        if(validateIsTargetExecution(recentMee, this.actualValue)) result.addAll(resultCandidate);
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

    static private boolean validateIsTargetExecution(MethodExitEvent recent, String actualValue){
        return getValueString(recent.returnValue()).equals(actualValue);
    };


    @Override
    protected Expression extractExpr() {
        try {
            if(!stmt.isReturnStmt()) throw new NoSuchElementException();
            return stmt.asReturnStmt().getExpression().orElseThrow();
        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract expression from [" + locateMethod + ":" + locateLine + "].");
        }
    }

    @Override
    public String toString(){
        return "[ SUSPICIOUS RETURN VALUE ]\n" + "    " + locateMethod.methodSignature + "{\n       ...\n" + super.toString() + "\n       ...\n    }";
    }

    @Override
    protected TracedValueCollection traceAllValuesAtSuspExpr(int sleepTime){
        System.out.println(" >>> [DEBUG] Return");
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
            StepRequest stepReq = EnhancedDebugger.createStepOutRequest(manager, thread);
            MethodExitRequest meReq = EnhancedDebugger.createMethodExitRequest(manager, thread);

            //直前に通知されたMethodExitEventを保持
            //StepEventでreturnから返った時にこのMEEを使ってreturnのactualValueを手にいれる。
            MethodExitEvent recentMee = null;

            //周辺の値を観測
            List<TracedValue> resultCandidate;
            try {
                resultCandidate = watchAllVariablesInLine(thread.frame(0));
            } catch (IncompatibleThreadStateException e) {
                throw new RuntimeException(e);
            }


            //resume してステップイベント/MethodExitを待つ
            vm.resume();
            boolean done = false;
            while (!done) {
                EventSet es = vm.eventQueue().remove();
                for (Event ev : es) {
                    if (ev instanceof MethodExitEvent) {
                        //直前に通知されたMethodExitEventを保持
                        MethodExitEvent mee = (MethodExitEvent) ev;
                        recentMee = mee;
                        vm.resume();
                    }
                    //調査対象の行の実行(実行インスタンス)が終了
                    //ここで、調査した行が目的のものであったかチェック
                    if (ev instanceof StepEvent) {
                        done = true;
                        if(recentMee == null){
                            //ここには到達しないはず
                            throw new RuntimeException("Something is wrong.");
                        }
                        System.out.println(" >>> [DEBUG] Return: " + getValueString(recentMee.returnValue()));
                        if(validateIsTargetExecution(recentMee, this.actualValue)) result.addAll(resultCandidate);
                        //vmをresumeしない
                    }
                }
            }
            //動的に作ったリクエストを無効化
            stepReq.disable();
            meReq.disable();
        };

        //VMを実行し情報を収集
        eDbg.handleAtBreakPoint(this.locateMethod.getFullyQualifiedClassName(), this.locateLine, handler);
        return TracedValuesAtLine.of(result);
    }
}
