package jisd.fl.infra.jvm;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class JVMProcess implements AutoCloseable{
    public final Process process;
    private final ExecutorService ioPool;
    private final Future<String> stdoutFuture;
    private final Future<String> stderrFuture;

    private JVMProcess(Process process, ExecutorService ioPool, Future<String> stdoutFuture, Future<String> stderrFuture) {
        this.process = process;
        this.ioPool = ioPool;
        this.stdoutFuture = stdoutFuture;
        this.stderrFuture = stderrFuture;
    }

    public static JVMProcess fromProcess(Process p){
        ExecutorService ioPool = Executors.newFixedThreadPool(2, r ->{
            Thread t = new Thread(r, "faultfinder-jvm-handle-io");
            t.setDaemon(true);
            return t;
        });

        Future<String> out = ioPool.submit(() -> readAll(p.getInputStream()));
        Future<String> err = ioPool.submit(() -> readAll(p.getErrorStream()));

        // プロセス終了を検知して、回収タスクの完了を待ってから shutdown
        CompletableFuture<Void> autoShutdown =
                p.onExit().thenRunAsync(() -> {
                    try {
                        out.get();
                        err.get();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException ee) {
                        // ここはログなどに流す設計でもOK
                    } finally {
                        ioPool.shutdown();
                    }
                });

        return new JVMProcess(p, ioPool, out, err);
    }

    public int waitFor() throws InterruptedException {
        return process.waitFor();
    }


    public String stdout() {
        return getQuietly(stdoutFuture);
    }

    public String stderr() {
        return getQuietly(stderrFuture);
    }

    private static String getQuietly(Future<String> f) {
        try {
            return f.get(50, TimeUnit.MILLISECONDS); // すでに読み終わってる前提なら短め
        } catch (TimeoutException e) {
            return ""; // 必要ならここは例外にしてもOK
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (BufferedInputStream bin = new BufferedInputStream(in);
             ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
            bin.transferTo(bout);
            return bout.toString(StandardCharsets.UTF_8);
        }
    }

    @Override
    public void close() {
        ioPool.shutdownNow();
    }
}
