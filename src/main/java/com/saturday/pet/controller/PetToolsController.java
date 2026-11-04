package com.saturday.pet.controller;

import com.saturday.pet.app.PetApp;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/pet/tools")
@Tag(name = "宠物助手工具调用")
public class PetToolsController {
    private final PetApp app;
    private final ToolCallback[] tools;

    public PetToolsController(PetApp app, @Qualifier("allTools") ToolCallback[] tools) {
        this.app = app;
        this.tools = tools;
    }

    @PostMapping("/chat")
    @Operation(summary = "通过大模型选择并执行六类工具")
    public String chat(@RequestBody PetAppController.ChatRequest request) {
        if (request.message() == null || request.message().isBlank() || request.chatId() == null || request.chatId().isBlank())
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "message 和 chatId 不能为空");
        return app.doChatWithTools(request.message(), request.chatId(), tools);
    }
}
