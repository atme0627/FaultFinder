package jisd.fl.infra.jacoco.testexec;

import jisd.fl.infra.jvm.JVMLaunchSpec;
import jisd.fl.infra.jvm.JVMLauncher;
import jisd.fl.infra.jvm.JVMProcess;
import jisd.fl.infra.jvm.JacocoTestExecServerLaunchSpecFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.time.Duration;

public class JacocoTestExecServerHandle implements Closeable {
    private final String host;
    private final int port;
    private final JVMProcess serverProcess;
    private final JacocoTestExecClient client;

    private JacocoTestExecServerHandle(
            String host,
            int port,
            JVMProcess serverProcess,
            JacocoTestExecClient client
    ) {
        this.host = host;
        this.port = port;
        this.serverProcess = serverProcess;
        this.client = client;
    }

    public static JacocoTestExecServerHandle startDefault(int port) throws IOException {
        return start("127.0.0.1", port, Duration.ofSeconds(5));
    }

    public static JacocoTestExecServerHandle start(String host, int port, Duration waitReady) throws IOException {
        JVMLaunchSpec spec = JacocoTestExecServerLaunchSpecFactory.defaultSpec(port);
        JVMProcess proc = JVMLauncher.launch(spec);

        // wait until server listens
        waitUntilReady(host, port, waitReady);

        JacocoTestExecClient client = new JacocoTestExecClient(host, port);

        return new JacocoTestExecServerHandle(host, port, proc, client);
    }

    public JacocoTestExecClient client() {
        return client;
    }

    @Override
    public void close() throws IOException {
        IOException first = null;

        // 1) try graceful quit
        try {
            client.quit(); // QUIT実装があるなら
        } catch (IOException e) {
            first = e;
        }

        // 2) ensure process termination
        try {
            terminateProcess();
        } catch (IOException e) {
            if (first == null) first = e;
        }

        if (first != null) throw first;
    }

    private static void waitUntilReady(String host, int port, Duration timeout) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        IOException last = null;

        while (System.nanoTime() < deadline) {
            try (Socket s = new Socket(host, port)) {
                return; // connected -> ready
            } catch (ConnectException e) {
                last = e;
            } catch (IOException e) {
                last = e;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting server ready", ie);
            }
        }

        if (last != null) throw new IOException("server not ready: " + host + ":" + port, last);
        throw new IOException("server not ready: " + host + ":" + port);
    }

    private void terminateProcess() throws IOException {
        Process p = serverProcess.process;
        if (!p.isAlive()) return;

        // まずは「自然終了」を待つ（QUIT が効いてればここで終わる）
        try {
            if (p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // interrupt でも一応フォールバックは続ける
        }

        // まだ生きてるなら、ここで初めて SIGTERM
        p.destroy();
        try {
            if (p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 最後の手段
        p.destroyForcibly();
        try {
            p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
