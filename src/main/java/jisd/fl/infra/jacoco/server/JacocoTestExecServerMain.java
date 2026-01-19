package jisd.fl.infra.jacoco.server;

import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.infra.junit.JUnitTestRunner;
import org.jacoco.agent.rt.IAgent;
import org.jacoco.agent.rt.RT;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * JacocoTestExecServerMain は、JaCoCo エージェントを用いてコードカバレッジの収集を行うためのサーバープログラムを提供する。
 * クライアントからのリクエストを受け付け、指定されたテストメソッドを実行し、結果とカバレッジデータを返す。
 *
 * このサーバーを正常に利用するためには、JVM 起動時に -javaagent オプションで JaCoCo エージェントを指定する必要がある。
 *
 * 主な機能:
 * - クライアントから送られた RUN コマンドで指定されたテストメソッドを実行。
 * - 実行結果（成功/失敗）およびテスト実行時のカバレッジデータをクライアントに送信。
 *
 * プロトコル仕様:
 * クライアントからサーバーへのコマンド:
 * - RUN <test_method_name> : 指定されたテストメソッドを実行
 *   - レスポンス: "OK <passed> <len>\n<bytes>" 
 *     - passed: テスト成功なら1、失敗なら0
 *     - len: 続くバイトデータの長さ
 *     - bytes: JaCoCoのカバレッジデータ
 *   - エラー時: "ERROR: <message>"
 * - QUIT : セッションを終了
 *   - レスポンス: "BYE"
 *
 */
public class JacocoTestExecServerMain {
    public static void main(String[] args) throws IOException {
        int port = parsePort(args, 30000);

        final IAgent agent;
        try {
            agent = RT.getAgent();
        }
        catch (Throwable t) {
            System.err.println("[Jacoco-exec-server] JaCoCo agent is not available. Did you pass -javaagent?");
            t.printStackTrace(System.err);
            return;
        }

        InetAddress bindAddr = InetAddress.getLoopbackAddress();
        try(ServerSocket server = new ServerSocket(port, 1, bindAddr)){
            System.out.println("[Jacoco-exec-server] Listening on " + bindAddr.getHostAddress() + ":" + port);
            while(true){
                try(Socket sock = server.accept()){
                    System.out.println("[Jacoco-exec-server] Client connected: " + sock.getRemoteSocketAddress());
                    handleClient(sock, agent);
                    System.out.println("[Jacoco-exec-server] Client disconnected");
                }
                catch (IOException e){
                    System.err.println("[Jacoco-exec-server] accept/handle error: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        }
    }

    /**
     * クライアントからの要求を処理する。
     *
     * @param sock クライアントソケット
     * @param agent コードカバレッジ収集を行うためのエージェント
     * @throws IOException 入出力エラーが発生した場合
     */
    private static void handleClient(Socket sock, IAgent agent) throws IOException{
        sock.setTcpNoDelay(true);

        BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
        OutputStream rawOut = new BufferedOutputStream(sock.getOutputStream());

        while(true){
            String line = in.readLine();
            if(line == null) return;
            line = line.trim();
            if(line.isEmpty()) continue;

            if(line.equals("QUIT")){
                writeLine(rawOut, "BYE");
                rawOut.flush();
                return;
            }

            if(line.startsWith("RUN ")){
                String fqTestMethodName = line.substring(4).trim();
                MethodElementName testMethod;
                if(fqTestMethodName.isEmpty()) {
                    writeLine(rawOut, "ERROR: empty test method name");
                    rawOut.flush();
                    continue;
                }
                try {
                    testMethod = new MethodElementName(fqTestMethodName);
                } catch (IllegalArgumentException e){
                    writeLine(rawOut, "ERROR: invalid test method name: " + fqTestMethodName);
                    rawOut.flush();
                    continue;
                }

                try {
                    agent.reset();
                    JUnitTestRunner.TestRunResult result = JUnitTestRunner.runSingleTest(testMethod, System.out);
                    byte[] exec = agent.getExecutionData(true);
                    // protocol: OK <passed> <len>\n <bytes>
                    writeLine(rawOut, "OK " + (result.passed() ? "1" : "0") + " " + exec.length);
                    rawOut.write(exec);
                    rawOut.flush();
                } catch (Throwable t) {
                    System.err.println("[Jacoco-exec-server] Error running test: " + testMethod);
                    t.printStackTrace(System.err);
                    writeLine(rawOut, "ERROR: " + sanitize(t.toString()));
                    rawOut.flush();
                }
                continue;
            }

            writeLine(rawOut, "ERROR: unknown command: " + line);
            rawOut.flush();
        }
    }

    private static void writeLine(OutputStream out, String s) throws IOException {
        out.write((s + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static int parsePort(String[] args, int defaultPort) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--port")) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        return defaultPort;
    }

    private static String sanitize(String s) {
        // avoid newlines in protocol
        return s.replace('\n', ' ').replace('\r', ' ');
    }
}

