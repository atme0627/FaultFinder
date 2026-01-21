package jisd.fl.infra.jdi;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.fl.core.domain.port.SuspiciousArgumentsSearcher;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.infra.javaparser.JavaParserSuspiciousExpressionFactory;
import jisd.fl.core.entity.susp.SuspiciousArgument;
import jisd.fl.infra.junit.JUnitDebugger;

import java.util.List;
import java.util.Optional;

public class JDISuspiciousArgumentsSearcher implements SuspiciousArgumentsSearcher {
    static public final SuspiciousExpressionFactory factory = new JavaParserSuspiciousExpressionFactory();

    /**
     * ある変数がその値を取る原因が呼び出し元の引数のあると判明した場合に使用
     */
    public Optional<SuspiciousArgument> searchSuspiciousArgument(SuspiciousVariable suspVar, MethodElementName calleeMethodName){
        //Debugger生成
        JUnitDebugger debugger = new JUnitDebugger(suspVar.getFailedTest());

        //探索によって求める値
        MethodElementName[] locateMethod = new MethodElementName[1];
        int[] locateLine = new int[1];
        int[] argIndex = new int[1];
        int[] callCountAfterTarget = new int[]{0};

        //調査対象の行実行に到達した時に行う処理を定義
        EnhancedDebugger.MethodEntryHandler handler = (vm, mEntry) -> {
            try {
                //呼び出しメソッドを取得
                ThreadReference thread = mEntry.thread();
                StackFrame topFrame = null;
                StackFrame callerFrame = null;
                try {
                    topFrame = thread.frame(0);
                    callerFrame = thread.frame(1);
                } catch (IncompatibleThreadStateException e) {
                    throw new RuntimeException(e);
                }

                //調査対象の変数がactualValueをとっているか確認
                LocalVariable topVar = topFrame.visibleVariableByName(suspVar.getSimpleVariableName());
                if(topVar == null) return;
                Value argValue = topFrame.getValue(topVar);
                if(!JDIUtils.getValueString(argValue).equals(suspVar.getActualValue())) return;
                //対象の引数のインデックスを取得
                List<LocalVariable> args = mEntry.method().arguments();
                for(int idx = 0; idx < args.size(); idx++){
                    if(args.get(idx).name().equals(suspVar.getSimpleVariableName())){
                        argIndex[0] = idx;
                    }
                }
                //引数に含まれない場合
                if(argIndex[0] == -1){
                    return;
                }

                com.sun.jdi.Location callerLoc = callerFrame.location();
                locateMethod[0] = new MethodElementName(EnhancedDebugger.getFqmn(callerLoc.method()));
                locateLine[0] = callerLoc.lineNumber();

                //targetVarが呼び出し元でどのexprに対応するかを特定
                //複数回同じメソッドが呼ばれている場合も考慮
                //目的のmethodEntryの後、何回methodが呼ばれるかを解析
                mEntry.request().disable();
                //この段階でリクエストは全てdisabledになっている必要がある。
                //今は呼び出されたメソッドの中にいる。
                callCountAfterTarget[0] = countMethodCallAfterTarget(vm, mEntry);

            } catch (AbsentInformationException e) {
                throw new RuntimeException(e);
            }
        };

        debugger.handleAtMethodEntry(calleeMethodName.fullyQualifiedName(), handler);

        //nullチェック
        if(locateMethod[0] == null || locateLine[0] == 0 || argIndex[0] == -1){
            System.err.println("Cannot find target argument of caller method. (may not be argument)\n" + suspVar);
            return Optional.empty();
        }
        return Optional.of(factory.createArgument(
                suspVar.getFailedTest(),
                locateMethod[0],
                locateLine[0],
                suspVar.getActualValue(),
                calleeMethodName,
                argIndex[0],
                callCountAfterTarget[0]
        ));
    }

    private static int countMethodCallAfterTarget(VirtualMachine vm, MethodEntryEvent mEntry) throws InterruptedException {
        int result = 0;
        EventRequestManager manager = vm.eventRequestManager();
        StepRequest stepOutReq = EnhancedDebugger.createStepOutRequest(manager, mEntry.thread());

        //一旦resumeして、内部ループでstep outを待つ
        vm.resume();

        //Methodがreturnし呼び出し元の行についた時点でsuspend
        //必ずMethodExitEventのみのはず
        Optional<Event> oev = vm.eventQueue().remove().stream().findFirst();
        if (oev.isEmpty() || !(oev.get() instanceof StepEvent)){
            throw new RuntimeException("something is wrong");
        }
        stepOutReq.disable();
        ThreadReference thisThread = ((StepEvent) oev.get()).thread();
        // ブレークポイント地点でのコールスタックの深さを取得
        // 呼び出しメソッドの取得条件を 深さ == depthBeforeCall + 1　にすることで
        // 再帰呼び出し含め、その行で直接呼ばれたメソッドの呼び出し回数をカウントするため
        int depthBeforeCall = JDIUtils.getCallStackDepth(thisThread);

        //この行の終わりを検知するためstepOverRequestを設置
        StepRequest stepOverReq = EnhancedDebugger.createStepOverRequest(manager, thisThread);
        //その後のメソッド呼び出し回数をカウントするためのMethodExitRequest
        MethodExitRequest mExit = EnhancedDebugger.createMethodExitRequest(manager, thisThread);

        //一旦resumeして、内部ループでMethodEntry / stepOverを待つ
        vm.resume();
        //イベントループ
        boolean done = false;
        while(!done) {
            EventSet es = vm.eventQueue().remove();
            for (Event ev : es) {
                //メソッド呼び出し回数をカウント
                if (ev instanceof MethodExitEvent) {
                    MethodExitEvent mee = (MethodExitEvent) ev;

                    //meeのthreadは抜ける直前のもののため+1が必要
                    if(JDIUtils.getCallStackDepth(mee.thread()) == depthBeforeCall + 1) result++;
                    vm.resume();
                    continue;
                }
                //調査対象の行の実行が終了
                if (ev instanceof StepEvent) {
                    done = true;
                    //vmをresumeしない
                }
            }
        }

        stepOverReq.disable();
        mExit.disable();
        return result;
    }
}
