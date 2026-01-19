package jisd.fl.infra.jacoco.exec;

import jisd.fl.core.entity.element.MethodElementName;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class JacocoTestExecClient implements Closeable {
    private final String host;
    private final int port;

    private Socket sock;
    private BufferedReader in;
    private OutputStream out;

    public JacocoTestExecClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    private void ensureConnected() throws IOException {
        if(sock != null && sock.isConnected() && !sock.isClosed()) return;

        sock = new Socket(InetAddress.getByName(host), port);
        sock.setTcpNoDelay(true);

        in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedOutputStream(sock.getOutputStream());
    }

    public TestExecReply runTest(MethodElementName testMethod) throws IOException {
        ensureConnected();

        writeLine("RUN " + testMethod.fullyQualifiedName());
        out.flush();

        String header = in.readLine();
        if (header == null) throw new EOFException("server closed connection");

        header = header.trim();
        if (header.startsWith("OK ")) {
            // OK <passed> <len>
            String[] parts = header.split(" ");
            if (parts.length != 3) {
                throw new IOException("malformed OK header: " + header);
            }
            boolean passed = parts[1].equals("1");
            int len;
            try {
                len = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                throw new IOException("malformed length: " + header, e);
            }

            byte[] exec = readExactly(len);
            return new TestExecReply(passed, exec);
        }

        if (header.startsWith("ERR")) {
            throw new IOException("server error: " + header);
        }

        if (header.equals("BYE")) {
            throw new IOException("server said BYE unexpectedly");
        }

        throw new IOException("unknown response: " + header);
    }

    public void quit() throws IOException {
        ensureConnected();
        writeLine("QUIT");
        out.flush();
        // server replies BYE (optional)
        String line = in.readLine();
        // ignore if null
        close();
    }

    private byte[] readExactly(int len) throws IOException {
        byte[] buf = new byte[len];
        int off = 0;

        InputStream rawIn = sock.getInputStream();
        while (off < len) {
            int r = rawIn.read(buf, off, len - off);
            if (r < 0) throw new EOFException("expected " + len + " bytes, got " + off);
            off += r;
        }
        return buf;
    }

    private void writeLine(String s) throws IOException {
        out.write((s + "\n").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() throws IOException {
        IOException first = null;
        if (out != null) {
            try { out.flush(); } catch (IOException e) { first = e; }
        }
        if (sock != null) {
            try { sock.close(); } catch (IOException e) { if (first == null) first = e; }
        }
        sock = null;
        in = null;
        out = null;
        if (first != null) throw first;
    }

    public record TestExecReply(boolean passed, byte[] execBytes) {}
}
