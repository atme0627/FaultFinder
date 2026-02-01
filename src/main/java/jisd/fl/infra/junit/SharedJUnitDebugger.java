package jisd.fl.infra.junit;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.infra.jdi.EnhancedDebugger;
import jisd.fl.infra.jdi.testexec.JDIDebugServerHandle;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * 共有 JVM セッション上で動作する JUnitDebugger。
 *
 * JVM の起動・破棄は {@link JDIDebugServerHandle} が管理するため、
 * このクラスでは close() は NO-OP とする。
 *
 * テスト実行は TCP 経由で RUN コマンドを送信し、
 * テスト完了は対象テストメソッドの MethodExitEvent で検知する。
 */
public class SharedJUnitDebugger extends EnhancedDebugger {
    private final JDIDebugServerHandle session;
    private final MethodElementName testMethod;
    private volatile boolean testCompleted = false;

    public SharedJUnitDebugger(JDIDebugServerHandle session, MethodElementName testMethod) {
        super(session.vm());
        this.session = session;
        this.testMethod = testMethod;
    }

    @Override
    public void execute(Supplier<Boolean> shouldStop) {
        testCompleted = false;

        // 1. テストメソッドの MethodExitEvent で完了検知用リクエスト設定
        setupTestCompletionDetection();

        // 2. ブレークポイント・ClassPrepareRequest 設定
        setupBreakpointsAndRequests();

        try {
            // 3. TCP で RUN 送信（応答は待たない）
            session.sendRunCommand(testMethod);

            // 4. イベントループ（shouldStop OR testCompleted で終了）
            Supplier<Boolean> combinedStop = () ->
                    testCompleted || (shouldStop != null && shouldStop.get());
            runEventLoop(combinedStop);

            // 5. イベントリクエストを削除し、debuggee を resume する。
            //    shouldStop で早期終了した場合、イベントループ脱出後に
            //    ブレークポイントにヒットして再 suspend されている可能性がある。
            session.cleanupEventRequests();
            try { vm.resume(); } catch (com.sun.jdi.VMDisconnectedException ignored) {}

            // 6. TCP 応答を読み取る（テスト完了まで待機）
            session.readRunResult();
        } catch (IOException e) {
            throw new RuntimeException("Failed to run test via session: " + testMethod, e);
        } finally {
            session.cleanupEventRequests();
            resetState();
            testCompleted = false;
        }
    }

    @Override
    public void execute() {
        execute(null);
    }

    /**
     * NO-OP: セッションがライフサイクルを管理する。
     */
    @Override
    public void close() {
        // do nothing
    }

    /**
     * テストメソッドの完了を MethodExitEvent で検知するリクエストを設定する。
     */
    private void setupTestCompletionDetection() {
        EventRequestManager mgr = vm.eventRequestManager();
        MethodExitRequest meReq = mgr.createMethodExitRequest();

        // テストクラスでフィルタ
        String testClassName = testMethod.fullyQualifiedClassName();
        meReq.addClassFilter(testClassName);
        meReq.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        meReq.enable();

        // MethodExitEvent ハンドラを登録
        String testMethodName = testMethod.shortMethodName();
        registerEventHandler(MethodExitEvent.class, (vm, ev) -> {
            MethodExitEvent mee = (MethodExitEvent) ev;
            if (mee.method().name().equals(testMethodName)) {
                testCompleted = true;
            }
        });
    }
}