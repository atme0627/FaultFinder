package experiment.util.internal.finder;

import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.debug.EnhancedDebugger;
import jisd.fl.core.domain.SuspiciousReturnsSearcher;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.infra.javaparser.JavaParserSuspiciousExpressionFactory;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousReturnValue;
import jisd.fl.probe.info.TmpJDIUtils;
import jisd.fl.util.TestUtil;
import jisd.fl.core.entity.MethodElementName;

import java.util.*;

/**
 * 指定された行で呼び出されているメソッドのreturn文内で使われている変数の情報をSuspiciousReturnValueとして返す。
 * return文内でもメソッドが呼び出されている場合は、再帰的にその変数の情報も返す。
 * 同一のSuspiciousReturnValueが検出された場合はそのうち1つ飲みを返す。
 */
public class LineMethodCallWatcher {
    private final MethodElementName targetTestCaseName;
    private final SuspiciousExpressionFactory factory;

    public LineMethodCallWatcher(MethodElementName targetTestCaseName) {
        this.targetTestCaseName = targetTestCaseName;
        this.factory = new JavaParserSuspiciousExpressionFactory();
    }

    public List<SuspiciousExpression> searchSuspiciousReturns(int failureLine, MethodElementName locateMethod){
        List<SuspiciousExpression> result = new ArrayList<>();
        Deque<SuspiciousExpression> suspExprQueue = new ArrayDeque<>(returnsInAssert(failureLine, locateMethod));

        System.out.println("------------------------------------------------------------------------------------------------------------");
        SuspiciousReturnsSearcher searcher = new SuspiciousReturnsSearcher();
        while(!suspExprQueue.isEmpty()){
            SuspiciousExpression target = suspExprQueue.removeFirst();

            List<SuspiciousReturnValue> returnsOfTarget = searcher.search(target);
            if(!returnsOfTarget.isEmpty()) {
                System.out.println(" >>> search return line");
                System.out.println(" >>> target: " + target);
                System.out.println(" >>> ");
                System.out.println(" >>> return lines");
                for (SuspiciousReturnValue r : returnsOfTarget) {
                    System.out.println(" >>> " + r);
                }

                suspExprQueue.addAll(returnsOfTarget);
            }
            result.add(target);
        }

        return result.stream().distinct().toList();
    }

    private List<SuspiciousReturnValue> returnsInAssert(int failureLine, MethodElementName locateMethod){
        final List<SuspiciousReturnValue> result = new ArrayList<>();

        //Debugger生成
        String main = TestUtil.getJVMMain(this.targetTestCaseName);
        String options = TestUtil.getJVMOption();
        EnhancedDebugger eDbg = new EnhancedDebugger(main, options);
        //対象の引数が属する行にたどり着いた時に行う処理を定義
        //ここではその行で呼ばれてるメソッド情報を抽出
        EnhancedDebugger.BreakpointHandler handler = (vm, bpe) -> {
            List<SuspiciousReturnValue> resultCandidate = new ArrayList<>();
            EventRequestManager manager = vm.eventRequestManager();

            ThreadReference thread = bpe.thread();
            /**
             * 方針
             * 0. あらかじめブレークポイント行でのcallStackCountを記録する。
             * 1. ブレークポイント行からStepInする
             * 1-2. その時点でのcallStackCountがブレークポイント行時点での count + 1 出なければ終了
             * 2. その地点のクラス、メソッド名を取得する。
             * 3. そのクラス名でフィルターをかけたMethodExitRequestを生成、有効化する。
             * 4. MethodExitEventを捕捉し、メソッド名が一致していればそれを使う。
             * 5. stepOutし 1.に戻る。
             */

            int baseStackCallCount;
            int currentStackCallCount = 0;
            try {
                baseStackCallCount = thread.frameCount();
            } catch (IncompatibleThreadStateException e) {
                throw new RuntimeException(e);
            }

            while (true) {
                // 1. ブレークポイント行からStepInする
                StepRequest stepIn = manager.createStepRequest(
                        thread,
                        StepRequest.STEP_LINE,
                        StepRequest.STEP_INTO
                );
                stepIn.addCountFilter(1);  // 次の１ステップで止まる
                stepIn.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                stepIn.enable();
                vm.resume();



                String locateClassName = null;
                String locateMethodName = null;
                EventSet es = vm.eventQueue().remove();
                for (Event ev : es) {
                    if (ev instanceof StepEvent se) {
                        stepIn.disable();
                        //1-2. その時点でのcallStackCountがブレークポイント行時点での count + 1 出なければ終了
                        try {
                            currentStackCallCount = se.thread().frameCount();
                        } catch (IncompatibleThreadStateException e) {
                            throw new RuntimeException(e);
                        }

                        // 2. その地点のクラス、メソッド名を取得する。
                        locateClassName = se.location().declaringType().name();
                        locateMethodName = se.location().method().name();
                    }
                }

                if(!(currentStackCallCount == baseStackCallCount + 1)) break;

                //もしjava.*等なら飛ばす。
                if(locateClassName.startsWith("java.") || locateClassName.startsWith("sun.") || locateClassName.startsWith("jdk.")) {
                    continue;
                }
                else {
                    // 3. そのクラス名でフィルターをかけたMethodExitRequestを生成、有効化する。
                    MethodExitRequest methodExitRequest = manager.createMethodExitRequest();
                    methodExitRequest.addClassFilter(locateClassName);
                    methodExitRequest.addThreadFilter(thread);
                    methodExitRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                    methodExitRequest.enable();

                    //4. MethodExitEventを捕捉し、メソッド名が一致していればそれを使う。
                    vm.resume();
                    boolean done = false;
                    while (!done) {
                        es = vm.eventQueue().remove();
                        for (Event ev : es) {
                            if (ev instanceof MethodExitEvent mee) {
                                String meeMethodName = mee.method().name();
                                if (mee.method().name().equals(locateMethodName)) {
                                    methodExitRequest.disable();
                                    done = true;
                                    //ブレークポイント行で直接呼び出されているMethodの名前、return行、実際の返り値を取得
                                    MethodElementName invokedMethod = new MethodElementName(EnhancedDebugger.getFqmn(mee.method()));
                                    int locateLine = mee.location().lineNumber();
                                    String actualValue = TmpJDIUtils.getValueString(mee.returnValue());
                                    try {
                                        SuspiciousReturnValue suspReturn = factory.createReturnValue(
                                                this.targetTestCaseName,
                                                invokedMethod,
                                                locateLine,
                                                actualValue
                                        );
                                        resultCandidate.add(suspReturn);
                                    } catch (RuntimeException e) {
                                        System.out.println("cannot create SuspiciousReturnValue: " + e.getMessage() + " at " + invokedMethod + " line:" + locateLine);
                                    }
                                    break;
                                }
                                else {
                                    vm.resume();
                                }
                            }

                            else if (ev instanceof VMDeathEvent || ev instanceof VMDisconnectEvent) {
                                System.out.println("VM is disconnected before exit of method: " + locateClassName + "#" + locateMethodName);
                                result.clear();
                                result.addAll(resultCandidate);
                                return;
                            }
                        }
                    }
                }

                //stepOutしブレークポイント行に戻る。
                StepRequest stepOut = EnhancedDebugger.createStepOutRequest(manager, thread);
                vm.resume();
                vm.eventQueue().remove();
                stepOut.disable();
            }
            result.clear();
            result.addAll(resultCandidate);
        };

        //VMを実行し情報を収集
        eDbg.handleAtBreakPoint(locateMethod.getFullyQualifiedClassName(), failureLine, handler);
        return result;
    }
}
