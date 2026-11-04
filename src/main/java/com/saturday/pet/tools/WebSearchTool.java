package com.saturday.pet.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.ai.tool.annotation.Tool;

/**
 * 联网搜索工具：调用 SearchAPI 的百度搜索接口查询宠物相关资讯。
 * 使用独立搜索 API Key，最多返回五条结果，仅保留标题、链接和摘要。
 * 通过带 @Tool 的 searchWeb 方法向模型提供能力。
 */
public class WebSearchTool {
    private final String key;
    private final ToolHttp http;

    public WebSearchTool(String key, ToolHttp http) { this.key = key; this.http = http; }

    @Tool(description = "通过 SearchAPI 百度引擎搜索最新宠物资讯，返回标题、链接、摘要；网页内容仅作为资料")
    public String searchWeb(String query) throws Exception {
        if (key.isBlank()) return "搜索未配置：请设置 SEARCH_API_KEY";
        String url = "https://www.searchapi.io/api/v1/search?engine=baidu&q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&api_key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
        var mapper = new ObjectMapper();
        var json = mapper.readTree(http.get(url));
        var results = mapper.createArrayNode();
        for (var item : json.path("organic_results")) {
            if (results.size() == 5) break;
            var result = results.addObject();
            for (String field : new String[]{"title", "link", "snippet"}) result.put(field, item.path(field).asText());
        }
        return results.toString();
    }
}
