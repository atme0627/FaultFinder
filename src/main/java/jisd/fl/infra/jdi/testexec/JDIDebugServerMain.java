package jisd.fl.infra.jdi.testexec;

import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.infra.jvm.TestExecServer;
import jisd.fl.infra.junit.JUnitTestRunner;

import java.io.IOException;
import java.io.OutputStream;

/**
 * JDI デバッグ用テスト実行サーバー。
 *
 * JDWP 付きで起動された JVM 内で動作し、TCP 経由でテスト実行指示を受け付ける。
 * 親プロセスは同じ JVM に JDWP でアタッチし、ブレークポイント・ステップ制御を行う。
 *
 * プロトコル:
 * - RUN <test_method_fqmn> : テストメソッドを実行
 *   - レスポンス: "OK <passed>" (passed: 1=成功, 0=失敗)
 *   - エラー時: "ERROR: <message>"
 * - QUIT : セッションを終了
 *   - レスポンス: "BYE"
 */
public class JDIDebugServerMain extends TestExecServer {

    public JDIDebugServerMain() {
        super("jdi-debug-server");
    }

    public static void main(String[] args) throws IOException {
        new JDIDebugServerMain().run(args);
    }

    @Override
    protected void initialize(String[] args) {
        // JDI サーバーは特別な初期化不要（JDWP は JVM 引数で有効化済み）
    }

    @Override
    protected int defaultPort() {
        return 30001;
    }

    @Override
    protected boolean handleCommand(String line, OutputStream out) throws IOException {
        if (line.startsWith("RUN ")) {
            String fqTestMethodName = line.substring(4).trim();
            MethodElementName testMethod = parseTestMethod(fqTestMethodName, out);
            if (testMethod == null) return true;

            try {
                JUnitTestRunner.TestRunResult result = runTest(testMethod);
                writeLine(out, "OK " + (result.passed() ? "1" : "0"));
                out.flush();
            } catch (Throwable t) {
                System.err.println("[jdi-debug-server] Error running test: " + testMethod);
                t.printStackTrace(System.err);
                writeLine(out, "ERROR: " + sanitize(t.toString()));
                out.flush();
            }
            return true;
        }
        return false;
    }
}