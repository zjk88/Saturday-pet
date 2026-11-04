package com.saturday.pet.tools;

import java.net.*;
import java.net.http.*;
import java.time.Duration;

/**
 * HTTP 请求辅助类：为联网搜索、网页抓取和资源下载提供公共 GET 请求。
 * 负责 HTTPS 地址校验、内网地址检查、请求超时和响应大小限制，并禁用自动重定向。
 * 自身不作为模型可调用工具。
 */
public class ToolHttp {
    public byte[] get(String url) throws Exception {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("只支持公网 HTTPS 地址");
        }
        for (var address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) throw new IllegalArgumentException("不允许内网地址");
        }
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
            byte[] data = body.readNBytes(5 * 1024 * 1024 + 1);
            if (data.length > 5 * 1024 * 1024) throw new IllegalArgumentException("资源超过 5MB");
            return data;
        }
    }
}
