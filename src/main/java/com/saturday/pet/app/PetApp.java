package com.saturday.pet.app;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import com.saturday.pet.advisor.MyLoggerAdvisor;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import reactor.core.publisher.Flux;
import org.springframework.util.Assert;

@Component
public class PetApp {
    private static final String SYSTEM_PROMPT = """
            你是 Saturday Pet 的宠物助手，名字叫“小六”。首次交流时简短介绍身份，使用友好、清楚的中文。
            帮助用户处理宠物日常喂养、护理、行为训练和养宠准备等问题。
            根据问题按需询问宠物名字、种类、品种、年龄、体重、生活环境和具体情况，避免一次提出过多问题。
            结合当前会话中用户已经提供的信息回答，不要反复询问已知信息，不要编造宠物资料。
            不要自称医生或虚构职业资质、从业年限与服务经历。
            涉及宠物健康时说明信息局限，不作确定诊断，不擅自提供处方用药剂量；
            对疑似紧急状况建议及时联系兽医。回答以具体、适量、可执行的建议为主。
            """;
    private final ChatClient chatClient;

    public PetApp(ChatModel chatModel) { this(chatModel, new InMemoryChatMemoryRepository()); }
    @Autowired
    public PetApp(ChatModel chatModel, ChatMemoryRepository repository) {
        // Spring AI 1.0 正式版使用消息窗口替代教程里旧版本的 InMemoryChatMemory。
        ChatMemory memory = MessageWindowChatMemory.builder().chatMemoryRepository(repository).maxMessages(10).build();
        chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build(), new MyLoggerAdvisor())
                .build();
    }

    // HSQLDB 的 conversation_id 最多 36 字符，保留可直接存储的历史键。
    static String memoryKey(String scopedId) {
        if (scopedId.length() <= 36) {
            return scopedId;
        }
        return UUID.nameUUIDFromBytes(scopedId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public String doChat(String message, String chatId) {
        return chatPrompt(message, chatId).call().content();
    }

    public Flux<String> doChatByStream(String message, String chatId) {
        return chatPrompt(message, chatId).stream().content();
    }

    private ChatClient.ChatClientRequestSpec chatPrompt(String message, String chatId) {
        Assert.hasText(message, "message 不能为空");
        Assert.hasText(chatId, "chatId 不能为空");
        return chatClient.prompt().user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, memoryKey(chatId)));
    }

    public record PetReport(String title, String petName, List<String> suggestions) {}
    public PetReport doChatWithReport(String message, String chatId) {
        Assert.hasText(message, "message required"); Assert.hasText(chatId, "chatId required");
        PetReport report = chatClient.prompt().user(message)
            .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, memoryKey(chatId)))
            .call().entity(PetReport.class);
        if (report == null || report.title() == null || report.title().isBlank()
            || report.suggestions() == null || report.suggestions().isEmpty())
            throw new IllegalStateException("Incomplete pet report");
        return report;
    }

    public String doChatWithRag(String message, String chatId,
                              org.springframework.ai.vectorstore.VectorStore store) {
        Assert.hasText(message, "message required");
        Assert.hasText(chatId, "chatId required");
        var advisor = org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
                .builder(store)
                .searchRequest(org.springframework.ai.vectorstore.SearchRequest.builder()
                        .topK(4).similarityThreshold(0.5).build())
                .build();
        return chatClient.prompt().user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, memoryKey("rag:" + chatId)))
                .advisors(advisor)
                .call().content();
    }

    public String doChatWithTools(String message, String chatId,
                                  org.springframework.ai.tool.ToolCallback[] tools) {
        Assert.hasText(message, "message required");
        Assert.hasText(chatId, "chatId required");
        return chatClient.prompt().user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, memoryKey("tools:" + chatId)))
                .toolCallbacks(tools)
                .call().content();
    }

    public String doChatWithMcp(String message, String chatId,
                                org.springframework.ai.tool.ToolCallback[] tools) {
        Assert.hasText(message, "message required");
        Assert.hasText(chatId, "chatId required");
        return chatClient.prompt()
                .system(SYSTEM_PROMPT + " 查询宠物医院时必须使用地图工具获取真实信息。"
                        + "用户未提供位置时先询问城市和地址，不猜测用户位置。"
                        + "列出可获取的医院名称、地址、距离和电话；缺少的字段明确说明。"
                        + "不能凭地图信息保证医院正在营业、提供急诊或具有特定诊疗能力，建议电话确认。")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, memoryKey("mcp:" + chatId)))
                .toolCallbacks(tools)
                .call().content();
    }
}

