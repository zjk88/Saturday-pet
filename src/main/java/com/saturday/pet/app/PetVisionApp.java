package com.saturday.pet.app;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

@Component
public class PetVisionApp {

    private final ChatClient client;
    private final String model;
    private boolean configured = true;

    @Autowired
    public PetVisionApp(
            @Value("${app.ai.vision.api-key:}") String apiKey,
            @Value("${app.ai.vision.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${app.ai.vision.model:deepseek-flash}") String model) {
        this(OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey(apiKey.isBlank() ? "not-configured" : apiKey)
                        .baseUrl(baseUrl)
                        .build())
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                .build(), model);
        configured = !apiKey.isBlank();
    }

    public PetVisionApp(ChatModel chatModel,
                        String model) {
        this.model = model;
        this.client = ChatClient.builder(chatModel)
                .defaultSystem("你是宠物助手小六。根据图片和问题回答，区分可见事实与推测。看不清时明确说明，不编造资料，不仅凭照片诊断疾病。")
                .build();
    }

    public String explain(String message, byte[] image, String mimeType) {
        if (!configured) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "请设置 DEEPSEEK_API_KEY 后重启，图片识别使用独立的 DeepSeek 配置");
        }
        return client.prompt()
                .user(user -> user.text(message)
                        .media(MimeType.valueOf(mimeType), new ByteArrayResource(image)))
                .options(OpenAiChatOptions.builder().model(model).build())
                .call()
                .content();
    }
}
