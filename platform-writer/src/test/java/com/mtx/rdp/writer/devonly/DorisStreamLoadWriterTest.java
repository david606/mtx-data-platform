package com.mtx.rdp.writer.devonly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DorisStreamLoadWriterTest {

    @Test
    void successRequiresDorisStatusAndLoadedRows() throws Exception {
        try (LocalFe fe = LocalFe.json(200, "{\"Status\":\"Success\",\"NumberLoadedRows\":1,\"NumberFilteredRows\":0}")) {
            DorisStreamLoadResult result = writer(fe).load("mtx_rdp_dev_only", "dev_only_result", "label-1", "{}".getBytes());
            assertTrue(result.success());
            assertEquals(1, result.loadedRows());
            assertEquals(0, result.filteredRows());
        }
    }

    @Test
    void httpOkWithFailedStatusIsNotSuccess() throws Exception {
        try (LocalFe fe = LocalFe.json(200, "{\"Status\":\"Fail\",\"Message\":\"filtered\",\"NumberLoadedRows\":0,\"NumberFilteredRows\":1}")) {
            DorisStreamLoadResult result = writer(fe).load("mtx_rdp_dev_only", "dev_only_result", "label-2", "{}".getBytes());
            assertFalse(result.success());
            assertEquals("Fail", result.streamStatus());
        }
    }

    @Test
    void redirectDropsUserInfoAndKeepsAuthorization() throws Exception {
        try (LocalFe be = LocalFe.json(200, "{\"Status\":\"Success\",\"NumberLoadedRows\":1,\"NumberFilteredRows\":0}");
                LocalFe fe = LocalFe.redirect(be, "http://dev_only_user:secret@127.0.0.1:" + be.port()
                        + "/api/db/table/_stream_load")) {
            DorisStreamLoadResult result = writer(fe, new DorisBeEndpoint("127.0.0.1", be.port()))
                    .load("mtx_rdp_dev_only", "dev_only_result", "label-3", "{}".getBytes());
            assertTrue(result.success());
            assertTrue(be.sawAuthorization());
            assertTrue(be.sawConnection());
            assertFalse(be.sawUserInfo());
        }
    }

    @Test
    void redirectToUnexpectedHostDoesNotSendAuthorization() throws Exception {
        try (LocalFe evil = LocalFe.json(200, "{\"Status\":\"Success\",\"NumberLoadedRows\":1,\"NumberFilteredRows\":0}");
                LocalFe fe = LocalFe.redirect(evil, "http://user:secret@127.0.0.1:" + evil.port() + "/api/db/table/_stream_load")) {
            DorisStreamLoadResult result = writer(fe, new DorisBeEndpoint("192.0.2.10", 8040))
                    .load("mtx_rdp_dev_only", "dev_only_result", "label-host", "{}".getBytes());
            assertFalse(result.success());
            assertEquals("Doris 重定向目标不在允许范围内", result.failureMessage());
            assertFalse(evil.sawConnection());
            assertFalse(result.failureMessage().contains("secret"));
            assertFalse(result.failureMessage().contains("Authorization"));
        }
    }

    @Test
    void redirectToUnexpectedPortDoesNotSendAuthorization() throws Exception {
        try (LocalFe evil = LocalFe.json(200, "{\"Status\":\"Success\",\"NumberLoadedRows\":1,\"NumberFilteredRows\":0}");
                LocalFe fe = LocalFe.redirect(evil, "http://user:secret@127.0.0.1:" + evil.port() + "/api/db/table/_stream_load")) {
            DorisStreamLoadResult result = writer(fe, new DorisBeEndpoint("127.0.0.1", 1))
                    .load("mtx_rdp_dev_only", "dev_only_result", "label-port", "{}".getBytes());
            assertFalse(result.success());
            assertEquals("Doris 重定向目标不在允许范围内", result.failureMessage());
            assertFalse(evil.sawConnection());
            assertFalse(result.failureMessage().contains("secret"));
        }
    }

    @Test
    void redirectToHttpsIsRejected() throws Exception {
        try (LocalFe evil = LocalFe.json(200, "{\"Status\":\"Success\",\"NumberLoadedRows\":1,\"NumberFilteredRows\":0}");
                LocalFe fe = LocalFe.redirect(evil, "https://127.0.0.1:" + evil.port() + "/api/db/table/_stream_load")) {
            DorisStreamLoadResult result = writer(fe, new DorisBeEndpoint("127.0.0.1", evil.port()))
                    .load("mtx_rdp_dev_only", "dev_only_result", "label-https", "{}".getBytes());
            assertFalse(result.success());
            assertEquals("Doris 重定向协议不被允许", result.failureMessage());
            assertFalse(evil.sawConnection());
        }
    }

    @Test
    void redirectWithoutLocationFails() throws Exception {
        try (LocalFe fe = LocalFe.redirect(null, null)) {
            DorisStreamLoadResult result = writer(fe, new DorisBeEndpoint("127.0.0.1", 8040))
                    .load("mtx_rdp_dev_only", "dev_only_result", "label-missing", "{}".getBytes());
            assertFalse(result.success());
            assertEquals("Doris 重定向缺少 Location", result.failureMessage());
        }
    }

    @Test
    void rootAccountIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DorisStreamLoadWriter("127.0.0.1", 8030, "root", "secret", Set.of()));
    }

    @Test
    void redirectUserInfoIsRemoved() {
        URI redirected = URI.create("http://dev_only_user:secret@127.0.0.1:8040/api/db/table/_stream_load");
        URI safe = DorisStreamLoadWriter.withoutUserInfo(redirected);
        assertEquals("127.0.0.1", safe.getHost());
        assertEquals(8040, safe.getPort());
        assertEquals("/api/db/table/_stream_load", safe.getPath());
        assertNull(safe.getUserInfo());
    }

    private static DorisStreamLoadWriter writer(LocalFe fe, DorisBeEndpoint... allowed) {
        return new DorisStreamLoadWriter("127.0.0.1", fe.port(), "dev_only_user", "secret", Set.of(allowed));
    }

    private static final class LocalFe implements AutoCloseable {
        private final ServerSocket server;
        private final Thread thread;
        private final AtomicBoolean sawAuthorization = new AtomicBoolean();
        private final AtomicBoolean sawUserInfo = new AtomicBoolean();
        private final AtomicBoolean sawConnection = new AtomicBoolean();

        private LocalFe(int status, String body, String location) throws IOException {
            server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            thread = new Thread(() -> serve(status, body, location));
            thread.start();
        }

        static LocalFe json(int status, String body) throws IOException {
            return new LocalFe(status, body, null);
        }

        static LocalFe redirect(LocalFe target, String location) throws IOException {
            if (target == null && location == null) {
                return new LocalFe(307, "", null);
            }
            return new LocalFe(307, "", location);
        }

        int port() {
            return server.getLocalPort();
        }

        boolean sawAuthorization() {
            return sawAuthorization.get();
        }

        boolean sawUserInfo() {
            return sawUserInfo.get();
        }

        boolean sawConnection() {
            return sawConnection.get();
        }

        @Override
        public void close() throws IOException {
            server.close();
            try {
                thread.join(1000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        private void serve(int status, String body, String location) {
            try (Socket socket = server.accept();
                    InputStream input = socket.getInputStream();
                    OutputStream output = socket.getOutputStream()) {
                sawConnection.set(true);
                String headers = readHeaders(input);
                sawAuthorization.set(headers.contains("Authorization:"));
                sawUserInfo.set(headers.contains("dev_only_user:secret"));
                if (location == null && headers.toLowerCase().contains("expect: 100-continue")) {
                    output.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    readBody(input, headers);
                }
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                StringBuilder response = new StringBuilder();
                response.append("HTTP/1.1 ").append(status).append(status == 307 ? " Temporary Redirect" : " OK").append("\r\n");
                if (location != null) {
                    response.append("Location: ").append(location).append("\r\n");
                }
                response.append("Content-Length: ").append(payload.length).append("\r\n");
                response.append("Connection: close\r\n\r\n");
                output.write(response.toString().getBytes(StandardCharsets.US_ASCII));
                output.write(payload);
                output.flush();
            } catch (IOException ignored) {
                // 测试结束时关闭监听端口。
            }
        }

        private static String readHeaders(InputStream input) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int matched = 0;
            byte[] marker = {'\r', '\n', '\r', '\n'};
            while (matched < marker.length) {
                int value = input.read();
                if (value < 0) {
                    break;
                }
                buffer.write(value);
                matched = value == marker[matched] ? matched + 1 : (value == '\r' ? 1 : 0);
            }
            return buffer.toString(StandardCharsets.US_ASCII);
        }

        private static void readBody(InputStream input, String headers) throws IOException {
            int length = 0;
            for (String line : headers.split("\r\n")) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    length = Integer.parseInt(line.substring("content-length:".length()).trim());
                }
            }
            int remaining = length;
            while (remaining > 0) {
                int read = input.read(new byte[Math.min(remaining, 256)]);
                if (read < 0) {
                    break;
                }
                remaining -= read;
            }
        }
    }
}
