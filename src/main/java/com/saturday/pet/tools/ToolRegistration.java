package com.saturday.pet.tools;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import java.nio.file.Path;

/**
 * 工具注册配置类：集中创建六类工具并转换为 Spring AI ToolCallback。
 * 注入搜索 API Key，复用公共文件管理和 HTTP 请求组件。
 * 向工具聊天接口提供 allTools Bean；自身不作为模型可调用工具。
 */
@Configuration
public class ToolRegistration {
    @Bean
    public ToolCallback[] allTools(@Value("${search-api.api-key:}") String key) throws Exception {
        var files = new ToolFiles(Path.of("tmp"));
        var http = new ToolHttp();
        return ToolCallbacks.from(new FileOperationTool(files), new WebSearchTool(key, http),
                new WebScrapingTool(http), new TerminalOperationTool(),
                new ResourceDownloadTool(files, http), new PDFGenerationTool(files));
    }
}
