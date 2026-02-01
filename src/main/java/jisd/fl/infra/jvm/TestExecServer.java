package jisd.fl.infra.jvm;

import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.infra.junit.JUnitTestRunner;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * TCP ベースのテスト実行サーバーの基底クラス。
 *
 * サーバーソケットの起動、クライアント接続の受付、コマンド読み取りループ、
 * QUIT 処理、親プロセス監視などの共通処理を提供する。
 *
 * サブクラスは {@link #initialize(String[])} でサーバー固有の初期化を行い、
 * {@link #handleCommand(String, OutputStream)} でサーバー固有のコマンドを処理する。
 *
 * 共通プロトコル:
 * - QUIT : セッションを終了。レスポンス: "BYE"
 * - その他のコマンドは {@link #handleCommand} にディスパッチされる。
 */
public abstract class TestExecServer {

    private final String serverName;

    protected TestExecServer(String serverName) {
        this.serverName = serverName;
    }

    /**
     * サーバー固有の初期化処理。
     * サーバーソケットを開く前に呼ばれる。
     * 初期化に失敗した場合は例外をスローすること。
     */
    protected abstract void initialize(String[] args) throws Exception;

    /**
     * QUIT 以外のコマンドを処理する。
     *
     * @param line   クライアントから受信したコマンド行（trim 済み、空行でない）
     * @param out    レスポンスを書き込む OutputStream
     * @return true: コマンドを処理した、false: 未知のコマンド（基底クラスがエラーを返す）
     */
    protected abstract boolean handleCommand(String line, OutputStream out) throws IOException;

    /**
     * サーバーを起動し、クライアント接続を待ち受ける。
     */
    public void run(String[] args) throws IOException {
        int port = parsePort(args, defaultPort());
        long ppid = parseLongArg(args, "--ppid", -1);
        if (ppid > 0) {
            startParentWatchdog(ppid);
        }

        try {
            initialize(args);
        } catch (Exception e) {
            System.err.println("[" + serverName + "] Initialization failed: " + e.getMessage());
            e.printStackTrace(System.err);
            return;
        }

        InetAddress bindAddr = InetAddress.getLoopbackAddress();
        try (ServerSocket server = new ServerSocket(port, 1, bindAddr)) {
            System.out.println("[" + serverName + "] Listening on " + bindAddr.getHostAddress() + ":" + port);
            while (true) {
                try (Socket sock = server.accept()) {
                    System.out.println("[" + serverName + "] Client connected: " + sock.getRemoteSocketAddress());
                    handleClient(sock);
                    System.out.println("[" + serverName + "] Client disconnected");
                } catch (IOException e) {
                    System.err.println("[" + serverName + "] accept/handle error: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        }
    }

    protected int defaultPort() {
        return 30000;
    }

    private void handleClient(Socket sock) throws IOException {
        sock.setTcpNoDelay(true);

        BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
        OutputStream rawOut = new BufferedOutputStream(sock.getOutputStream());

        while (true) {
            String line = in.readLine();
            if (line == null) return;
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.equals("QUIT")) {
                writeLine(rawOut, "BYE");
                rawOut.flush();
                return;
            }

            if (!handleCommand(line, rawOut)) {
                writeLine(rawOut, "ERROR: unknown command: " + line);
                rawOut.flush();
            }
        }
    }

    // --- RUN コマンドのヘルパー（サブクラスから利用） ---

    /**
     * RUN コマンドのテストメソッド名をパースする。
     * 空文字列や不正な形式の場合はエラーレスポンスを返し null を返す。
     */
    protected MethodElementName parseTestMethod(String fqTestMethodName, OutputStream out) throws IOException {
        if (fqTestMethodName.isEmpty()) {
            writeLine(out, "ERROR: empty test method name");
            out.flush();
            return null;
        }
        try {
            return new MethodElementName(fqTestMethodName);
        } catch (IllegalArgumentException e) {
            writeLine(out, "ERROR: invalid test method name: " + fqTestMethodName);
            out.flush();
            return null;
        }
    }

    /**
     * テストを実行し、結果を返す。
     */
    protected JUnitTestRunner.TestRunResult runTest(MethodElementName testMethod) {
        return JUnitTestRunner.runSingleTest(testMethod, System.out);
    }

    // --- ユーティリティ ---

    protected static void writeLine(OutputStream out, String s) throws IOException {
        out.write((s + "\n").getBytes(StandardCharsets.UTF_8));
    }

    protected static String sanitize(String s) {
        return s.replace('\n', ' ').replace('\r', ' ');
    }

    protected static int parsePort(String[] args, int defaultPort) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--port")) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        return defaultPort;
    }

    protected static long parseLongArg(String[] args, String key, long def) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(key)) return Long.parseLong(args[i + 1]);
        }
        return def;
    }

    private void startParentWatchdog(long ppid) {
        Thread t = new Thread(() -> {
            while (true) {
                boolean alive = ProcessHandle.of(ppid)
                        .map(ProcessHandle::isAlive)
                        .orElse(false);
                if (!alive) {
                    System.err.println("[" + serverName + "] parent died -> exit");
                    System.exit(0);
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }, "parent-watchdog");
        t.setDaemon(true);
        t.start();
    }
}