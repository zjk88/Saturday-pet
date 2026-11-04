package com.saturday.pet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/ai")
@Tag(name = "大模型测试")
public class AiController {
    private final ChatClient client;
    private final String apiKey;

    public AiController(ChatClient.Builder builder,
                        @Value("${spring.ai.openai.api-key}") String apiKey) {
        this.client = builder.build();
        this.apiKey = apiKey;
    }

    @GetMapping("/chat")
    @Operation(summary = "调用通义千问，验证 Spring AI 接入")
    public String chat(@RequestParam(defaultValue = "请用中文回答：1加1等于几？") String message) {
        if ("not-configured".equals(apiKey) || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "请在运行配置中设置 AI_DASHSCOPE_API_KEY 环境变量后重启");
        }
        return client.prompt().user(message).call().content();
    }
}
