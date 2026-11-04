package com.saturday.pet.controller;

import com.saturday.pet.app.PetApp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/pet")
@Tag(name = "宠物助手多轮对话")
public class PetAppController {
    private final PetApp petApp;

    public PetAppController(PetApp petApp) {
        this.petApp = petApp;
    }

    @PostMapping("/chat")
    @Operation(summary = "多轮对话：相同 chatId 保留上下文，不同 chatId 隔离")
    public String chat(@RequestBody ChatRequest request) {
        validate(request);
        return petApp.doChat(request.message(), request.chatId());
    }

    private void validate(ChatRequest request) {
        if (request.message() == null || request.message().isBlank()
                || request.chatId() == null || request.chatId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message 和 chatId 不能为空");
        }
    }

    @GetMapping("/chat/sync")
    @Operation(summary = "同步返回完整对话回复")
    public String chatSync(@RequestParam String message, @RequestParam String chatId) {
        return chat(new ChatRequest(message, chatId));
    }

    @GetMapping(value = "/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "逐段返回宠物助手的回复")
    public Flux<ServerSentEvent<String>> chatStream(@RequestParam String message, @RequestParam String chatId) {
        validate(new ChatRequest(message, chatId));
        return petApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.builder(chunk).build());
    }

    @PostMapping("/report")
    @Operation(summary = "生成结构化宠物报告")
    public PetApp.PetReport report(@RequestBody ChatRequest request) {
        return petApp.doChatWithReport(request.message(), request.chatId());
    }

    public record ChatRequest(String message, String chatId) {}
}
