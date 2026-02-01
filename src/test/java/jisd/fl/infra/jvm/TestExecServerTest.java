package jisd.fl.infra.jvm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TestExecServer 基底クラスの共通処理（QUIT、未知コマンド、空行処理等）をテストする。
 * テスト用のダミーサブクラスを使用する。
 */
@Timeout(30)
class TestExecServerTest {

    private Thread serverThread;
    private int serverPort;

    @AfterEach
    void tearDown() {
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    /**
     * テスト用のダミーサーバー。
     * ECHO コマンドを受け取り、引数をそのまま返す。
     */
    static class EchoServer extends TestExecServer {
        boolean initialized = false;

        EchoServer() {
            super("test-echo-server");
        }

        @Override
        protected void initialize(String[] args) {
            initialized = true;
        }

        @Override
        protected boolean handleCommand(String line, OutputStream out) throws IOException {
            if (line.startsWith("ECHO ")) {
                String payload = line.substring(5);
                writeLine(out, "OK " + payload);
                out.flush();
                return true;
            }
            return false;
        }
    }

    private int startServer(TestExecServer server) throws Exception {
        int port = findFreePort();
        serverThread = new Thread(() -> {
            try {
                server.run(new String[]{"--port", String.valueOf(port)});
            } catch (IOException e) {
                // サーバー終了
            }
        }, "test-server");
        serverThread.setDaemon(true);
        serverThread.start();
        waitUntilReady(port);
        this.serverPort = port;
        return port;
    }

    @Test
    void quit_command_returns_bye() throws Exception {
        startServer(new EchoServer());

        try (Socket sock = connect()) {
            send(sock, "QUIT");
            String response = readLine(sock);
            assertEquals("BYE", response);
        }
    }

    @Test
    void echo_command_returns_payload() throws Exception {
        startServer(new EchoServer());

        try (Socket sock = connect()) {
            send(sock, "ECHO hello world");
            String response = readLine(sock);
            assertEquals("OK hello world", response);

            send(sock, "QUIT");
            assertEquals("BYE", readLine(sock));
        }
    }

    @Test
    void unknown_command_returns_error() throws Exception {
        startServer(new EchoServer());

        try (Socket sock = connect()) {
            send(sock, "INVALID_CMD");
            String response = readLine(sock);
            assertTrue(response.startsWith("ERROR:"), "Expected ERROR response, got: " + response);

            send(sock, "QUIT");
            assertEquals("BYE", readLine(sock));
        }
    }

    @Test
    void empty_lines_are_ignored() throws Exception {
        startServer(new EchoServer());

        try (Socket sock = connect()) {
            send(sock, "");
            send(sock, "  ");
            send(sock, "ECHO test");
            String response = readLine(sock);
            assertEquals("OK test", response);

            send(sock, "QUIT");
            assertEquals("BYE", readLine(sock));
        }
    }

    @Test
    void multiple_commands_in_session() throws Exception {
        startServer(new EchoServer());

        try (Socket sock = connect()) {
            send(sock, "ECHO first");
            assertEquals("OK first", readLine(sock));

            send(sock, "ECHO second");
            assertEquals("OK second", readLine(sock));

            send(sock, "QUIT");
            assertEquals("BYE", readLine(sock));
        }
    }

    @Test
    void initialize_is_called() throws Exception {
        EchoServer server = new EchoServer();
        assertFalse(server.initialized);
        startServer(server);
        assertTrue(server.initialized);
    }

    // --- ヘルパー ---

    private Socket connect() throws IOException {
        Socket sock = new Socket(InetAddress.getLoopbackAddress(), serverPort);
        sock.setTcpNoDelay(true);
        return sock;
    }

    private static void send(Socket sock, String line) throws IOException {
        OutputStream out = sock.getOutputStream();
        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String readLine(Socket sock) throws IOException {
        InputStream in = sock.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(128);
        while (true) {
            int b = in.read();
            if (b < 0) return bos.size() == 0 ? null : bos.toString(StandardCharsets.UTF_8);
            if (b == '\n') break;
            if (b != '\r') bos.write(b);
        }
        return bos.toString(StandardCharsets.UTF_8);
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static void waitUntilReady(int port) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            try (Socket s = new Socket(InetAddress.getLoopbackAddress(), port)) {
                // 接続確認後すぐ切断（サーバーが accept してくれる）
                s.getOutputStream().write("QUIT\n".getBytes(StandardCharsets.UTF_8));
                s.getOutputStream().flush();
                return;
            } catch (IOException e) {
                Thread.sleep(100);
            }
        }
        throw new RuntimeException("Server did not start within timeout");
    }
}