package experiment.util.internal.finder;

import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import jisd.debug.EnhancedDebugger;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousReturnValue;
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.JavaParserUtil;
import jisd.fl.util.analyze.MethodElementName;

import java.nio.file.NoSuchFileException;
import java.util.*;

public class LineMethodCallWatcher {
    private final MethodElementName targetTestCaseName;
    public LineMethodCallWatcher(MethodElementName targetTestCaseName) {
        this.targetTestCaseName = targetTestCaseName;
    }

    public List<SuspiciousExpression> searchSuspiciousReturns(int failureLine, MethodElementName locateMethod){
        List<SuspiciousExpression> result = new ArrayList<>();
        int callCount = getMethodCallCount(failureLine, locateMethod);
        Deque<SuspiciousExpression> suspExprQueue = new ArrayDeque<>(returnsInAssert(failureLine, locateMethod, callCount));

        System.out.println("------------------------------------------------------------------------------------------------------------");
        while(!suspExprQueue.isEmpty()){
            SuspiciousExpression target = suspExprQueue.removeFirst();

            List<SuspiciousReturnValue> returnsOfTarget = target.searchSuspiciousReturns();
            if(!returnsOfTarget.isEmpty()) {
                System.out.println(" >>> search return line");
                System.out.println(" >>> target: " + target);
                System.out.println(" >>> ");
                System.out.println(" >>> return lines");
                for (SuspiciousReturnValue r : returnsOfTarget) {
                    System.out.println(" >>> " + r);
                }
                target.addChild(returnsOfTarget);
                suspExprQueue.addAll(returnsOfTarget);
            }
            result.add(target);
        }

        return result.stream().distinct().toList();
    }

    private List<SuspiciousReturnValue> returnsInAssert(int failureLine, MethodElementName locateMethod, int callCount){
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
             * あらかじめその行で行われるメソッド呼び出しの回数を特定し、その回数だけ以下を行う。
             * 1. ブレークポイント行からStepInする
             * 2. その地点のクラス、メソッド名を取得する。
             * 3. そのクラス名でフィルターをかけたMethodExitRequestを生成、有効化する。
             * 4. MethodExitEventを捕捉し、メソッド名が一致していればそれを使う。
             * 5. stepOutし 1.に戻る。
             */


            for (int i = 0; i < callCount; i++) {
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

                // 2. その地点のクラス、メソッド名を取得する。
                String locateClassName = null;
                String locateMethodName = null;
                EventSet es = vm.eventQueue().remove();
                for (Event ev : es) {
                    if (ev instanceof StepEvent se) {
                        stepIn.disable();
                        locateClassName = se.location().declaringType().name();
                        locateMethodName = se.location().method().name();
                    }
                }

                //もしjava.*等なら飛ばす。
                if(locateClassName.startsWith("java.") || locateClassName.startsWith("sun.") || locateClassName.startsWith("jdk.")) {
                    i--;
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
                                    String actualValue = SuspiciousExpression.getValueString(mee.returnValue());
                                    try {
                                        SuspiciousReturnValue suspReturn = new SuspiciousReturnValue(
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

    private int getMethodCallCount(int failureLine, MethodElementName locateMethod){
        try {
            Statement stmt = JavaParserUtil.getStatementByLine(locateMethod, failureLine).orElseThrow();
            return stmt.findAll(MethodCallExpr.class).size();
        } catch (NoSuchFileException e) {
            throw new RuntimeException("Class [" + locateMethod + "] is not found.");
        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract Statement from [" + locateMethod + ":" + failureLine + "].");
        }
    }

}
