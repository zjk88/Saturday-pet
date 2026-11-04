package com.saturday.pet.tools;

import java.nio.file.*;
import org.springframework.ai.tool.annotation.Tool;

/**
 * 资源下载工具：从公网 HTTPS 地址下载资源到 tmp/download。
 * 复用 ToolHttp 的网络限制和 ToolFiles 的文件名校验，不覆盖已有文件。
 * 通过带 @Tool 的 downloadResource 方法向模型提供能力。
 */
public class ResourceDownloadTool {
    private final ToolFiles files;
    private final ToolHttp http;

    public ResourceDownloadTool(ToolFiles files, ToolHttp http) { this.files = files; this.http = http; }

    @Tool(description = "将公网 HTTPS 资源下载到 tmp/download，不覆盖已有文件，最大 5MB")
    public String downloadResource(String url, String fileName) throws Exception {
        var path = files.resolve("download", fileName);
        byte[] data = http.get(url);
        Files.write(path, data, StandardOpenOption.CREATE_NEW);
        return "Downloaded: " + path;
    }
}
