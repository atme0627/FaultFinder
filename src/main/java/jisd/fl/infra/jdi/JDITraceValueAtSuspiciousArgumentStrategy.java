package jisd.fl.infra.jdi;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import jisd.fl.core.domain.port.TraceValueAtSuspiciousExpressionStrategy;
import jisd.fl.core.entity.susp.SuspiciousArgument;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.util.TestUtil;

import java.util.ArrayList;
import java.util.List;

public class JDITraceValueAtSuspiciousArgumentStrategy implements TraceValueAtSuspiciousExpressionStrategy {

    public List<TracedValue> traceAllValuesAtSuspExpr(SuspiciousExpression suspExpr){
        SuspiciousArgument suspArg = (SuspiciousArgument) suspExpr;
        final List<TracedValue> result = new ArrayList<>();

        //Debugger生成
        String main = TestUtil.getJVMMain(suspArg.failedTest);
        String options = TestUtil.getJVMOption();
        EnhancedDebugger eDbg = new EnhancedDebugger(main, options);
        //調査対象の行実行に到達した時に行う処理を定義
        EnhancedDebugger.BreakpointHandler handler = (vm, bpe) -> {
            //既に情報が取得できている場合は終了
            if(!result.isEmpty()) return;

            EventRequestManager manager = vm.eventRequestManager();

            //メソッドの呼び出しが行われたことを検知するステップリクエストを作成
            //目的の行であったかの判断は、メソッドに入った時の引数の値で確認する。
            //calleeのMethodEntryの通知タイミングで、今調査していた行が調べたい行だったかを確認
            ThreadReference thread = bpe.thread();
            MethodEntryRequest mEntryReq = EnhancedDebugger.createMethodEntryRequest(manager, thread);

            //周辺の値を観測
            List<TracedValue> resultCandidate;
            try {
                StackFrame frame = thread.frame(0);
                resultCandidate = JDIUtils.watchAllVariablesInLine(frame, suspArg.locateLine);
            } catch (IncompatibleThreadStateException e) {
                throw new RuntimeException(e);
            }

            //resume してステップイベントを待つ
            vm.resume();
            boolean done = false;
            while (!done) {
                EventSet es = vm.eventQueue().remove();
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
                                    .equals(suspArg.calleeMethodName.getFullyQualifiedClassName());
                        } else {
                            isTarget = method.name().equals(suspArg.calleeMethodName.getShortMethodName());
                        }

                        //entryしたメソッドが目的のcalleeメソッドか確認
                        if(isTarget) {
                            if (JDIUtils.validateIsTargetExecutionArg(mEntry, suspArg.actualValue, suspArg.argIndex)) {
                                done = true;
                                result.addAll(resultCandidate);
                            }
                            else {
                                //ここに到達した時点で、今回の実行は目的の実行でなかった
                                done = true;
                            }
                        }
                        else {
                            vm.resume();
                        }

                    }
                }
            };
            //動的に作ったリクエストを無効化;
            mEntryReq.disable();
        };

        //VMを実行し情報を収集
        eDbg.handleAtBreakPoint(suspArg.locateMethod.getFullyQualifiedClassName(), suspArg.locateLine, handler);
        return result;
    }
}
