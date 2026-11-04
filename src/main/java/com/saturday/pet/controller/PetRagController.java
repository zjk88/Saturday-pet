package com.saturday.pet.controller;

import com.saturday.pet.app.PetApp;
import com.saturday.pet.rag.PetKnowledgeBase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pet/rag")
@Tag(name = "宠物本地知识库")
public class PetRagController {
    private final PetApp app;
    private final PetKnowledgeBase knowledgeBase;

    public PetRagController(PetApp app, PetKnowledgeBase knowledgeBase) {
        this.app = app;
        this.knowledgeBase = knowledgeBase;
    }

    @PostMapping("/chat")
    @Operation(summary = "检索本地 Markdown 知识库后回答")
    public String chat(@RequestBody PetAppController.ChatRequest request) {
        if (request.message() == null || request.message().isBlank()
                || request.chatId() == null || request.chatId().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "message 和 chatId 不能为空");
        }
        return app.doChatWithRag(request.message(), request.chatId(), knowledgeBase.getStore());
    }
}
