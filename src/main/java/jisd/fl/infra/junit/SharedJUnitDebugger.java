package jisd.fl.infra.junit;

import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.infra.jdi.EnhancedDebugger;
import jisd.fl.infra.jdi.testexec.JDIDebugServerHandle;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 共有 JVM セッション上で動作する JUnitDebugger。
 *
 * JVM の起動・破棄は {@link JDIDebugServerHandle} が管理するため、
 * このクラスでは close() は NO-OP とする。
 *
 * テスト実行は TCP 経由で RUN コマンドを送信し、
 * テスト完了は TCP 応答の受信で検知する。
 * （MethodExitEvent はテスト失敗時に発火しないため使用しない）
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

        // 1. ブレークポイント・ClassPrepareRequest 設定
        setupBreakpointsAndRequests();

        try {
            // 2. TCP で RUN 送信（応答は待たない）
            session.sendRunCommand(testMethod);

            // 3. TCP 応答を別スレッドで読み取り、完了フラグを立てる
            CompletableFuture<Void> tcpResult = CompletableFuture.runAsync(() -> {
                try {
                    session.readRunResult();
                } catch (IOException e) {
                    // テスト完了自体は検知できたので、例外は無視
                } finally {
                    testCompleted = true;
                }
            });

            // 4. イベントループ（shouldStop OR testCompleted で終了）
            System.err.println("[SESSION-DEBUG] before eventLoop: testCompleted=" + testCompleted);
            Supplier<Boolean> combinedStop = () ->
                    testCompleted || (shouldStop != null && shouldStop.get());
            runEventLoop(combinedStop);

            // 5. イベントリクエストを削除し、debuggee を resume する。
            //    shouldStop で早期終了した場合、イベントループ脱出後に
            //    ブレークポイントにヒットして再 suspend されている可能性がある。
            System.err.println("[SESSION-DEBUG] after eventLoop: testCompleted=" + testCompleted + " shouldStop=" + (shouldStop != null ? shouldStop.get() : "null"));
            session.cleanupEventRequests();
            try { vm.resume(); } catch (com.sun.jdi.VMDisconnectedException ignored) {}

            // 6. TCP 応答の完了を確実に待つ
            tcpResult.join();
            System.err.println("[SESSION-DEBUG] after tcpResult.join()");

            // 7. EventQueue に残存するイベントを読み捨てる
            session.drainEventQueue();
            System.err.println("[SESSION-DEBUG] after drainEventQueue()");
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
}
