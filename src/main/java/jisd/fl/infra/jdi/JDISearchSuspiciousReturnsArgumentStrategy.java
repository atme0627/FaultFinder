package jisd.fl.infra.jdi;

import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.Method;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.debug.EnhancedDebugger;
import jisd.fl.core.domain.port.SearchSuspiciousReturnsStrategy;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.infra.javaparser.JavaParserSuspiciousExpressionFactory;
import jisd.fl.probe.info.*;
import jisd.fl.util.TestUtil;

import java.util.ArrayList;
import java.util.List;

public class JDISearchSuspiciousReturnsArgumentStrategy implements SearchSuspiciousReturnsStrategy {

    //引数のindexを指定してその引数の評価の直前でsuspendするのは激ムズなのでやらない
    //引数を区別せず、引数の評価の際に呼ばれたすべてのメソッドについて情報を取得し
    //Expressionを静的解析してexpressionで直接呼ばれてるメソッドのみに絞る
    //ex.) expressionがx.f(y.g())の時、fのみとる。y.g()はfの探索の後行われるはず
    @Override
    public List<SuspiciousReturnValue> search(SuspiciousExpression suspExpr) {
        SuspiciousArgument suspArg = (SuspiciousArgument) suspExpr;
        SuspiciousExpressionFactory factory = new JavaParserSuspiciousExpressionFactory();
        final List<SuspiciousReturnValue> result = new ArrayList<>();
        if(!(suspArg).hasMethodCalling()) return result;

        //探索対象のmethod名リストを取得
        List<String> targetMethodName = (suspArg).targetMethodNames();
        //対象の引数内の最初のmethodCallがstmtで何番目か
        int targetCallCount = (suspArg).targetCallCount;
        //methodCallの回数をカウント
        int[] callCount = new int[]{0};

        //Debugger生成
        String main = TestUtil.getJVMMain((suspArg).failedTest);
        String options = TestUtil.getJVMOption();
        EnhancedDebugger eDbg = new EnhancedDebugger(main, options);
        //調査対象の行実行に到達した時に行う処理を定義
        EnhancedDebugger.BreakpointHandler handler = (vm, bpe) -> {
            //既に情報が取得できている場合は終了
            if(!result.isEmpty()) return;

            List<SuspiciousReturnValue> resultCandidate = new ArrayList<>();
            EventRequestManager manager = vm.eventRequestManager();

            //このスレッドでの MethodExit を記録するリクエストを作成
            ThreadReference thread = bpe.thread();
            MethodExitRequest meReq = EnhancedDebugger.createMethodExitRequest(manager, thread);
            //メソッドの呼び出しが行われたことを検知するステップリクエストを作成
            //ステップイベントの通知タイミングで、今調査していた行が調べたい行だったかを確認
            StepRequest stepReq = EnhancedDebugger.createStepOverRequest(manager, thread);
            //目的の行であったかの判断は、メソッドに入った時の引数の値で確認する。
            MethodEntryRequest mEntryReq = EnhancedDebugger.createMethodEntryRequest(manager, thread);

            //一旦 resume して、内部ループで MethodExit／Step を待つ
            vm.resume();

            //直前に通知されたMethodEntryEventを保持

            boolean done = false;
            while (!done) {
                EventSet es = vm.eventQueue().remove();
                boolean doResume = true;
                for (Event ev : es) {
                    //あるメソッドに入った
                    if(ev instanceof MethodEntryEvent){
                        //かつ対象の引数が目的の値を取っている場合、目的の行実行であったとし探索終了
                        MethodEntryEvent mEntry = (MethodEntryEvent) ev;

                        // 1) 通常メソッドの場合は name() で比較
                        // 2) コンストラクタの場合は declaringType().name()（FQCN）で比較
                        boolean isTarget;
                        Method method = mEntry.method();
                        if (method.isConstructor()) {
                            // calleeMethodName には FullyQualifiedClassName を保持している想定
                            isTarget = method.declaringType().name()
                                    .equals((suspArg).calleeMethodName.getFullyQualifiedClassName());
                        } else {
                            isTarget = method.name().equals((suspArg).calleeMethodName.getShortMethodName());
                        }

                        //entryしたメソッドが目的のcalleeメソッドか確認
                        if(isTarget) {
                            if (JDIUtils.validateIsTargetExecutionArg(mEntry, (suspArg).actualValue, (suspArg).argIndex)) {
                                done = true;
                                result.addAll(resultCandidate);
                                //vmをresumeしない
                                doResume = false;
                            }
                            else {
                                //ここに到達した時点で、今回の実行は目的の実行でなかった
                                done = true;
                                //vmをresumeしない
                                doResume = false;
                            }
                        }
                    }
                    //あるメソッドから抜けた
                    if (ev instanceof MethodExitEvent) {
                        callCount[0]++;
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
                        if (mee.thread().equals(thread) && caller.location().method().equals(bpe.location().method())) {
                            if(callCount[0] >= targetCallCount) {
                                //targetMethodのみ収集
                                if (targetMethodName.contains(mee.method().name())) {
                                    MethodElementName invokedMethod = new MethodElementName(EnhancedDebugger.getFqmn(mee.method()));
                                    int locateLine = mee.location().lineNumber();
                                    String actualValue = JDIUtils.getValueString(mee.returnValue());
                                    try {
                                        SuspiciousReturnValue suspReturn = factory.createReturnValue(
                                                (suspArg).failedTest,
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
                            }
                        }
                    }
                    //調査対象の行の実行が終了
                    //ここに到達した時点で、今回の実行は目的の実行でなかった
                    if (ev instanceof StepEvent) {
                        done = true;
                        //vmをresumeしない
                        doResume = false;
                    }
                }
                if(doResume){
                    vm.resume();
                }
            }
            //動的に作ったリクエストを無効化
            meReq.disable();
            stepReq.disable();
            mEntryReq.disable();
        };

        //VMを実行し情報を収集
        eDbg.handleAtBreakPoint((suspArg).locateMethod.getFullyQualifiedClassName(), (suspArg).locateLine, handler);
        if(result.isEmpty()){
            System.err.println("[[searchSuspiciousReturns]] Could not confirm [ "
                    + "(return value) == " + (suspArg).actualValue
                    + " ] on " + (suspArg).locateMethod + " line:" + (suspArg).locateLine);
        }
        return result;
    }
}
