package com.mtx.rdp.writer.devonly;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * 用 JDK HTTP 客户端调用 Doris FE Stream Load。不引入 Doris Java 客户端。
 * 认证信息只放在请求头，不进入异常文本。
 */
public final class DorisStreamLoadWriter {

    static {
        // Doris FE 拒绝没有 Expect: 100-continue 的 Stream Load。
        // JDK HttpClient 默认禁止该头，必须在客户端类初始化前放开。
        String allowed = System.getProperty("jdk.httpclient.allowRestrictedHeaders", "");
        if (!allowed.toLowerCase().contains("expect")) {
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", allowed.isBlank() ? "expect" : allowed + ",expect");
        }
    }

    private final HttpClient client;
    private final String feHost;
    private final int feHttpPort;
    private final String username;
    private final String password;

    public DorisStreamLoadWriter(String feHost, int feHttpPort, String username, String password) {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                feHost, feHttpPort, username, password);
    }

    DorisStreamLoadWriter(HttpClient client, String feHost, int feHttpPort, String username, String password) {
        if (client == null) {
            throw new IllegalArgumentException("HTTP 客户端不能为空");
        }
        if (feHost == null || feHost.isBlank() || feHttpPort <= 0) {
            throw new IllegalArgumentException("Doris FE 地址无效");
        }
        rejectRoot(username);
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Doris 密码不能为空");
        }
        this.client = client;
        this.feHost = feHost;
        this.feHttpPort = feHttpPort;
        this.username = username;
        this.password = password;
    }

    public DorisStreamLoadResult load(String database, String table, String label, byte[] body) {
        if (database == null || database.isBlank() || table == null || table.isBlank()) {
            throw new IllegalArgumentException("Doris 库表不能为空");
        }
        if (label == null || label.isBlank() || body == null) {
            throw new IllegalArgumentException("Stream Load 标签和正文不能为空");
        }
        URI uri = URI.create("http://" + feHost + ":" + feHttpPort + "/api/" + database + "/" + table + "/_stream_load");
        try {
            HttpResponse<byte[]> response = send(uri, label, body);
            if (isRedirect(response.statusCode())) {
                Optional<String> location = response.headers().firstValue("location");
                if (location.isEmpty()) {
                    return DorisStreamLoadResult.failure(response.statusCode(), "", "Doris 重定向缺少 Location");
                }
                response = send(withoutUserInfo(URI.create(location.get())), label, body);
            }
            return interpret(response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return DorisStreamLoadResult.failure(0, "", "Doris Stream Load 被中断");
        } catch (IOException exception) {
            return DorisStreamLoadResult.failure(0, "", "Doris Stream Load 请求失败");
        }
    }

    private HttpResponse<byte[]> send(URI uri, String label, byte[] body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", authorization())
                .header("Expect", "100-continue")
                .header("label", label)
                .header("format", "json")
                .header("strip_outer_array", "false")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private DorisStreamLoadResult interpret(HttpResponse<byte[]> response) {
        String body = new String(response.body(), StandardCharsets.UTF_8);
        String status = jsonString(body, "Status");
        long loaded = jsonLong(body, "NumberLoadedRows");
        long filtered = jsonLong(body, "NumberFilteredRows");
        boolean success = response.statusCode() == 200
                && "Success".equals(status)
                && loaded >= 1
                && filtered == 0;
        if (success) {
            return new DorisStreamLoadResult(true, response.statusCode(), status, loaded, filtered, "");
        }
        String message = jsonString(body, "Message");
        if (message.isBlank()) {
            message = jsonString(body, "msg");
        }
        if (message.isBlank()) {
            message = "Doris 没有返回成功状态";
        }
        return new DorisStreamLoadResult(false, response.statusCode(), status, loaded, filtered, message);
    }

    private String authorization() {
        String token = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private static void rejectRoot(String username) {
        if (username == null || username.isBlank() || "root".equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("Doris 应用账号不能为空，也不能使用 root");
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 307 || status == 308;
    }

    static URI withoutUserInfo(URI uri) {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getRawPath(), uri.getRawQuery(), null);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Doris 重定向地址无效");
        }
    }

    static String jsonString(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyAt = json.indexOf(marker);
        if (keyAt < 0) {
            return "";
        }
        int colon = json.indexOf(':', keyAt + marker.length());
        if (colon < 0) {
            return "";
        }
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) {
            return "";
        }
        int end = json.indexOf('"', quote + 1);
        if (end < 0) {
            return "";
        }
        return json.substring(quote + 1, end);
    }

    static long jsonLong(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyAt = json.indexOf(marker);
        if (keyAt < 0) {
            return -1;
        }
        int colon = json.indexOf(':', keyAt + marker.length());
        if (colon < 0) {
            return -1;
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end == start) {
            return -1;
        }
        return Long.parseLong(json.substring(start, end));
    }
}
