package jisd.fl.infra.jdi.testexec;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.request.*;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.infra.jvm.JDIDebugServerLaunchSpecFactory;
import jisd.fl.infra.jvm.JVMLaunchSpec;
import jisd.fl.infra.jvm.JVMLauncher;
import jisd.fl.infra.jvm.JVMProcess;

import jisd.fl.infra.junit.SharedJUnitDebugger;

import java.io.*;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JDI デバッグサーバーセッション。
 *
 * JDWP + TCP のデュアル接続で debuggee JVM を管理する。
 * 複数の Strategy 実行にわたって単一の JVM を再利用できる。
 *
 * <pre>
 * try (JDIDebugServerHandle session = JDIDebugServerHandle.start()) {
 *     session.runTest(testMethod);  // TCP で RUN 送信
 *     // JDWP でブレークポイント・ステップ制御
 *     session.cleanupEventRequests();  // 次の Strategy 用にリセット
 * }
 * </pre>
 */
public class JDIDebugServerHandle implements Closeable {

    private static volatile JDIDebugServerHandle shared;

    private final JVMProcess process;
    private final VirtualMachine vm;
    private final Socket tcpSocket;
    private final BufferedReader tcpIn;
    private final OutputStream tcpOut;

    private JDIDebugServerHandle(JVMProcess process, VirtualMachine vm,
                                Socket tcpSocket, BufferedReader tcpIn, OutputStream tcpOut) {
        this.process = process;
        this.vm = vm;
        this.tcpSocket = tcpSocket;
        this.tcpIn = tcpIn;
        this.tcpOut = tcpOut;
    }

    /** Probe が呼ぶ。共有セッションを起動する。 */
    public static JDIDebugServerHandle startShared() throws IOException {
        if (shared != null) throw new IllegalStateException("shared session already exists");
        shared = start();
        return shared;
    }

    /** Strategy が呼ぶ。共有セッションから debugger を生成する。 */
    public static SharedJUnitDebugger createSharedDebugger(MethodElementName testMethod) {
        if (shared == null) throw new IllegalStateException("shared session not started");
        return new SharedJUnitDebugger(shared, testMethod);
    }

    public static JDIDebugServerHandle start() throws IOException {
        int tcpPort = findFreePort();
        int jdwpPort = findFreePort();
        return start("127.0.0.1", tcpPort, jdwpPort, Duration.ofSeconds(10));
    }

    public static JDIDebugServerHandle start(String host, int tcpPort, int jdwpPort,
                                            Duration waitReady) throws IOException {
        // 1. JVM 起動
        String jdwpAddress = host + ":" + jdwpPort;
        JVMLaunchSpec spec = JDIDebugServerLaunchSpecFactory.create(tcpPort, jdwpAddress);
        JVMProcess proc = JVMLauncher.launch(spec);

        // 2. TCP 接続可能になるまで待機
        waitUntilReady(host, tcpPort, waitReady);

        // 3. TCP 接続
        Socket sock = new Socket(InetAddress.getByName(host), tcpPort);
        sock.setTcpNoDelay(true);
        BufferedReader in = new BufferedReader(
                new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
        OutputStream out = new BufferedOutputStream(sock.getOutputStream());

        // 4. JDWP アタッチ
        VirtualMachine vm = attachJDWP(host, String.valueOf(jdwpPort));

        return new JDIDebugServerHandle(proc, vm, sock, in, out);
    }

    public VirtualMachine vm() {
        return vm;
    }

    /**
     * TCP 経由でテスト実行を指示する。
     * テスト完了まで待ち、結果を返す。
     *
     * @return true: テスト成功、false: テスト失敗
     */
    public boolean runTest(MethodElementName testMethod) throws IOException {
        sendRunCommand(testMethod);
        return readRunResult();
    }

    /**
     * TCP 経由でテスト実行コマンドを送信する（応答は待たない）。
     * JDWP でイベントループを回しながらテストを実行する場合に使用。
     * 応答は {@link #readRunResult()} で後から読み取る。
     */
    public void sendRunCommand(MethodElementName testMethod) throws IOException {
        String cmd = "RUN " + testMethod.fullyQualifiedName() + "\n";
        System.err.println("[TCP-DEBUG] sendRunCommand: " + cmd.trim() + " at " + System.currentTimeMillis());
        tcpOut.write(cmd.getBytes(StandardCharsets.UTF_8));
        tcpOut.flush();
    }

    /**
     * TCP の RUN コマンドに対する応答を読み取る。
     * {@link #sendRunCommand(MethodElementName)} の後に呼び出す。
     *
     * @return true: テスト成功、false: テスト失敗
     */
    public boolean readRunResult() throws IOException {
        long t0 = System.currentTimeMillis();
        String response = tcpIn.readLine();
        long t1 = System.currentTimeMillis();
        System.err.println("[TCP-DEBUG] readRunResult: response=\"" + response + "\" waitMs=" + (t1 - t0) + " at " + t1);
        if (response == null) {
            throw new EOFException("server closed connection");
        }
        response = response.trim();

        if (response.startsWith("OK ")) {
            return response.equals("OK 1");
        }
        if (response.startsWith("ERROR")) {
            throw new IOException("server error: " + response);
        }
        throw new IOException("unexpected response: " + response);
    }

    /**
     * 全てのイベントリクエストを削除する。
     * Strategy 実行間のクリーンアップに使用。
     */
    public void cleanupEventRequests() {
        EventRequestManager mgr = vm.eventRequestManager();
        mgr.deleteAllBreakpoints();
        for (StepRequest r : mgr.stepRequests()) mgr.deleteEventRequest(r);
        for (MethodExitRequest r : mgr.methodExitRequests()) mgr.deleteEventRequest(r);
        for (MethodEntryRequest r : mgr.methodEntryRequests()) mgr.deleteEventRequest(r);
        for (ClassPrepareRequest r : mgr.classPrepareRequests()) mgr.deleteEventRequest(r);
    }

    /**
     * EventQueue に残っているイベントを全て読み捨てる。
     * EventRequest 削除後もキューに入済みのイベントは残るため、
     * 次の Strategy 実行前に呼び出して残存イベントの干渉を防ぐ。
     */
    public void drainEventQueue() {
        EventQueue queue = vm.eventQueue();
        int count = 0;
        while (true) {
            try {
                EventSet es = queue.remove(1);
                if (es == null) break;
                count++;
                for (var ev : es) {
                    System.err.println("[DRAIN-DEBUG] drained: " + ev.getClass().getSimpleName());
                }
                es.resume();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.err.println("[DRAIN-DEBUG] total drained: " + count + " event sets");
    }

    @Override
    public void close() throws IOException {
        if (shared == this) shared = null;
        IOException first = null;

        // 1. TCP で QUIT 送信
        try {
            tcpOut.write("QUIT\n".getBytes(StandardCharsets.UTF_8));
            tcpOut.flush();
        } catch (IOException e) {
            first = e;
        }

        // 2. TCP ソケットを閉じる
        try {
            tcpSocket.close();
        } catch (IOException e) {
            if (first == null) first = e;
        }

        // 3. VM を dispose
        try {
            if (vm != null) {
                vm.dispose();
            }
        } catch (VMDisconnectedException ignored) {
        }

        // 4. プロセスを終了
        try {
            terminateProcess();
        } catch (IOException e) {
            if (first == null) first = e;
        }

        if (first != null) throw first;
    }

    // --- private ---

    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static void waitUntilReady(String host, int port, Duration timeout) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        IOException last = null;

        while (System.nanoTime() < deadline) {
            try (Socket s = new Socket(host, port)) {
                return;
            } catch (IOException e) {
                last = e;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting for server", ie);
            }
        }

        if (last != null) throw new IOException("server not ready: " + host + ":" + port, last);
        throw new IOException("server not ready: " + host + ":" + port);
    }

    private static VirtualMachine attachJDWP(String host, String port) throws IOException {
        var vmManager = Bootstrap.virtualMachineManager();
        AttachingConnector socket = vmManager.attachingConnectors().stream()
                .filter(c -> c.name().equals("com.sun.jdi.SocketAttach"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("SocketAttach connector not found"));

        Map<String, Connector.Argument> args = socket.defaultArguments();
        args.get("hostname").setValue(host);
        args.get("port").setValue(port);

        int maxTry = 50;
        long sleepMs = 100;

        for (int i = 0; i < maxTry; i++) {
            try {
                return socket.attach(args);
            } catch (IOException e) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while attaching JDWP", ie);
                }
            } catch (IllegalConnectorArgumentsException e) {
                throw new IOException("invalid JDWP arguments", e);
            }
        }
        throw new IOException("Failed to attach JDWP. host=" + host + " port=" + port);
    }

    private void terminateProcess() throws IOException {
        Process p = process.process;
        if (!p.isAlive()) return;

        try {
            if (p.waitFor(2, TimeUnit.SECONDS)) return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        p.destroy();
        try {
            if (p.waitFor(2, TimeUnit.SECONDS)) return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        p.destroyForcibly();
        try {
            p.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
