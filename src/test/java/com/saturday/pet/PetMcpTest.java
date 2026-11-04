package com.saturday.pet;

import com.saturday.pet.app.PetApp;
import com.saturday.pet.controller.PetMcpController;
import com.saturday.pet.controller.PetAppController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.support.ToolCallbacks;
import com.saturday.pet.tools.TerminalOperationTool;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class PetMcpTest {
    @Test
    void disabledMcpReturnsServiceUnavailable() {
        var factory = new DefaultListableBeanFactory();
        var controller = new PetMcpController(mock(PetApp.class), factory.getBeanProvider(ToolCallbackProvider.class));
        assertThatThrownBy(() -> controller.chat(new PetAppController.ChatRequest("查找医院", "a")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("503");
    }

    @Test
    void forwardsDiscoveredToolsToModel() {
        var model = mock(ChatModel.class);
        when(model.getDefaultOptions()).thenReturn(org.springframework.ai.openai.OpenAiChatOptions.builder().model("test").build());
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("请提供城市和地址")))));
        var factory = new DefaultListableBeanFactory();
        var callbacks = ToolCallbacks.from(new TerminalOperationTool());
        factory.registerSingleton("testMcpProvider", (ToolCallbackProvider) () -> callbacks);
        var controller = new PetMcpController(new PetApp(model), factory.getBeanProvider(ToolCallbackProvider.class));
        assertThat(controller.chat(new PetAppController.ChatRequest("附近医院", "a"))).contains("地址");
        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(((ToolCallingChatOptions) prompt.getValue().getOptions()).getToolCallbacks()).hasSize(1);
        assertThat(prompt.getValue().getSystemMessage().getText()).contains("地图工具", "不猜测用户位置");
    }
}
