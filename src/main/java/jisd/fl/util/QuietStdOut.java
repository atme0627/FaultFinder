package jisd.fl.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class QuietStdOut implements AutoCloseable {
    private final PrintStream prevOut;
    private final PrintStream prevErr;

    private static final OutputStream NULL_OS = OutputStream.nullOutputStream();

    // コンストラクタで抑制
    public QuietStdOut() {
        this.prevOut = System.out;
        this.prevErr = System.err;
        PrintStream nop = new PrintStream(NULL_OS);
        System.setOut(nop);
        System.setErr(nop);
    }

    // try-with-resources 終了時に自動で呼ばれる
    @Override
    public void close() {
        System.setOut(prevOut);
        System.setErr(prevErr);
    }

    // 簡易ファクトリ
    public static QuietStdOut suppress() {
        return new QuietStdOut();
    }
    public static QuietStdOut suppress(String msg) {
        System.out.println(msg);
        return new QuietStdOut();
    }
}