package com.saturday.pet.controller;

import com.saturday.pet.app.PetApp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Arrays;

/**
 * 地图 MCP 聊天入口：从 MCP 客户端获得工具，交由宠物助手查询医院及位置。
 * MCP 未启用时返回配置提示，不影响其他聊天接口。
 */
@RestController
@RequestMapping("/pet/mcp")
@Tag(name = "宠物医院地图 MCP")
public class PetMcpController {

    private final PetApp app;
    private final ObjectProvider<ToolCallbackProvider> providers;

    public PetMcpController(PetApp app, ObjectProvider<ToolCallbackProvider> providers) {
        this.app = app;
        this.providers = providers;
    }

    @PostMapping("/chat")
    @Operation(summary = "通过高德 MCP 查询宠物医院，需提供城市或具体位置")
    public String chat(@RequestBody PetAppController.ChatRequest request) {
        if (request.message() == null || request.message().isBlank()
                || request.chatId() == null || request.chatId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message 和 chatId 不能为空");
        }
        ToolCallback[] tools = providers.orderedStream()
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .toArray(ToolCallback[]::new);
        if (tools.length == 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "请启用 mcp profile 并配置 AMAP_MAPS_API_KEY 后重启");
        }
        return app.doChatWithMcp(request.message(), request.chatId(), tools);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<java.util.Map<String, String>> handleStatus(
            ResponseStatusException exception) {
        return org.springframework.http.ResponseEntity.status(exception.getStatusCode())
                .body(java.util.Map.of("message", exception.getReason() == null
                        ? "MCP 请求失败" : exception.getReason()));
    }
}
