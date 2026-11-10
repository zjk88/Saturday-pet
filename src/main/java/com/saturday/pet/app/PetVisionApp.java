package com.saturday.pet.app;

import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.web.client.RestClientException;

@Component
public class PetVisionApp {
    private static final Logger log = LoggerFactory.getLogger(PetVisionApp.class);
    // Spring AI 1.0 prefixes provider errors with the HTTP status, followed by the raw body.
    private static final Pattern PROVIDER_STATUS = Pattern.compile("^([45][0-9]{2}) - ");

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
        try {
            return client.prompt()
                    .user(user -> user.text(message)
                            .media(MimeType.valueOf(mimeType), new ByteArrayResource(image)))
                    .options(OpenAiChatOptions.builder().model(model).build())
                    .call()
                    .content();
        } catch (NonTransientAiException | TransientAiException exception) {
            var status = PROVIDER_STATUS.matcher(exception.getMessage() == null ? "" : exception.getMessage());
            int code = status.find() ? Integer.parseInt(status.group(1)) : 0;
            log.warn("Vision provider request failed, HTTP status={}", code == 0 ? "unknown" : code);
            String reason = switch (code) {
                case 401, 403 -> "图片识别鉴权失败，请检查后端的 DeepSeek Key 和权限";
                case 402 -> "图片识别账户余额不足";
                case 400, 404 -> "图片识别服务拒绝了请求，请检查视觉模型配置和后端日志";
                case 429 -> "图片识别请求过于频繁，请稍后再试";
                default -> "图片识别服务暂时不可用，请稍后再试";
            };
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, reason);
        } catch (RestClientException exception) {
            log.warn("Vision provider connection failed, type={}", exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "后端暂时无法连接图片识别服务");
        }
    }
}
