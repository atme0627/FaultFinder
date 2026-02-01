package jisd.fl.infra.jacoco.testexec;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.infra.junit.JUnitTestFinder;
import jisd.fl.infra.junit.JUnitTestRunner;
import jisd.fl.infra.jvm.TestExecServer;
import org.jacoco.agent.rt.IAgent;
import org.jacoco.agent.rt.RT;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * JaCoCo エージェントを用いてコードカバレッジの収集を行うためのサーバープログラム。
 * クライアントからのリクエストを受け付け、指定されたテストメソッドを実行し、結果とカバレッジデータを返す。
 *
 * このサーバーを正常に利用するためには、JVM 起動時に -javaagent オプションで JaCoCo エージェントを指定する必要がある。
 *
 * プロトコル仕様:
 * - RUN <test_method_name> : 指定されたテストメソッドを実行
 *   - レスポンス: "OK <passed> <len>\n<bytes>"
 *     - passed: テスト成功なら1、失敗なら0
 *     - len: 続くバイトデータの長さ
 *     - bytes: JaCoCo のカバレッジデータ
 *   - エラー時: "ERROR: <message>"
 * - LIST <test_class_name> : 指定されたテストクラスに含まれるテストメソッドの一覧を取得
 *   - レスポンス: "OK <count>\n<method_name>..."
 *   - エラー時: "ERR <message>"
 * - QUIT : セッションを終了
 *   - レスポンス: "BYE"
 */
public class JacocoTestExecServerMain extends TestExecServer {

    private IAgent agent;

    public JacocoTestExecServerMain() {
        super("Jacoco-exec-server");
    }

    public static void main(String[] args) throws IOException {
        new JacocoTestExecServerMain().run(args);
    }

    @Override
    protected void initialize(String[] args) throws Exception {
        try {
            this.agent = RT.getAgent();
        } catch (Throwable t) {
            throw new IllegalStateException("JaCoCo agent is not available. Did you pass -javaagent?", t);
        }
    }

    @Override
    protected boolean handleCommand(String line, OutputStream out) throws IOException {
        if (line.startsWith("RUN ")) {
            handleRun(line.substring(4).trim(), out);
            return true;
        }
        if (line.startsWith("LIST ")) {
            handleList(line.substring(5).trim(), out);
            return true;
        }
        return false;
    }

    private void handleRun(String fqTestMethodName, OutputStream out) throws IOException {
        MethodElementName testMethod = parseTestMethod(fqTestMethodName, out);
        if (testMethod == null) return;

        try {
            agent.reset();
            JUnitTestRunner.TestRunResult result = runTest(testMethod);
            byte[] exec = agent.getExecutionData(true);
            writeLine(out, "OK " + (result.passed() ? "1" : "0") + " " + exec.length);
            out.write(exec);
            out.flush();
        } catch (Throwable t) {
            System.err.println("[Jacoco-exec-server] Error running test: " + testMethod);
            t.printStackTrace(System.err);
            writeLine(out, "ERROR: " + sanitize(t.toString()));
            out.flush();
        }
    }

    private void handleList(String testClassFqcn, OutputStream out) throws IOException {
        if (testClassFqcn.isEmpty()) {
            writeLine(out, "ERR empty class");
            out.flush();
            return;
        }

        ClassElementName testClass = new ClassElementName(testClassFqcn);
        try {
            System.out.println("[Jacoco-exec-server] test class: " + testClass.fullyQualifiedName());
            List<MethodElementName> methods = JUnitTestFinder.getTestMethods(testClass);
            writeLine(out, "OK " + methods.size());
            for (MethodElementName m : methods) {
                System.out.println("[Jacoco-exec-server] method: " + m);
                writeLine(out, m.fullyQualifiedName());
            }
            out.flush();
        } catch (IllegalArgumentException e) {
            writeLine(out, "ERROR " + sanitize(e.getMessage()));
            out.flush();
        }
    }
}