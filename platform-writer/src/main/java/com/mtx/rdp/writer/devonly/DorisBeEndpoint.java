package com.mtx.rdp.writer.devonly;

/**
 * 允许接收 Stream Load 重定向的 Doris BE。只比较调用方给出的主机和端口，不解析 DNS。
 */
public record DorisBeEndpoint(String host, int port) {

    public DorisBeEndpoint {
        if (host == null || host.isBlank() || host.indexOf('/') >= 0 || host.indexOf('@') >= 0
                || host.indexOf(':') >= 0 || host.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Doris BE 主机无效");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Doris BE 端口无效");
        }
    }
}
