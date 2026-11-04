package com.saturday.pet;

import com.saturday.pet.app.PetApp;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import java.util.List;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PetAppTest {
    @Test
    void retainsConversationAndIsolatesOtherSessions() {
        ChatModel model = mock(ChatModel.class);
        List<Prompt> prompts = new ArrayList<>();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0));
            return new ChatResponse(List.of(new Generation(new AssistantMessage("收到"))));
        });
        PetApp app = new PetApp(model);
        app.doChat("你好，我是小明", "session-a");
        app.doChat("我的宠物叫小红", "session-a");
        app.doChat("我的宠物叫什么？", "session-a");
        assertThat(prompts.get(2).getInstructions().toString()).contains("小明", "小红");
        app.doChat("你好", "session-b");
        assertThat(prompts.get(3).getInstructions().toString()).doesNotContain("小明", "小红");
        for (int i = 0; i < 8; i++) app.doChat("后续消息" + i, "session-a");
        assertThat(prompts.get(prompts.size() - 1).getInstructions().toString()).doesNotContain("小明", "小红");
    }
}
