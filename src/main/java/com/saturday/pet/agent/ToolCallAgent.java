package com.saturday.pet.agent;

import java.util.Arrays;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;

/** 手动驱动工具调用，将真实执行结果放入下一轮模型请求。 */
public abstract class ToolCallAgent extends ReActAgent {
    private final ChatModel model;
    private final List<ToolCallback> tools;
    private final ToolCallingManager manager = ToolCallingManager.builder().build();

    public static class FinishTool {
        @Tool(description = "任务已结束时单独调用，answer 为提供给用户的最终答复")
        public String terminate(String answer) {
            return answer;
        }
    }

    protected ToolCallAgent(ChatModel model, ToolCallback[] callbacks) {
        this.model = model;
        var available = new java.util.ArrayList<>(Arrays.asList(callbacks));
        available.addAll(Arrays.asList(ToolCallbacks.from(new FinishTool())));
        tools = List.copyOf(available);
    }

    private Prompt prompt(Task task) {
        var options = ToolCallingChatOptions.builder().toolCallbacks(tools)
                .internalToolExecutionEnabled(false).build();
        return new Prompt(List.copyOf(task.history), options);
    }

    @Override
    protected ChatResponse think(Task task) {
        return model.call(prompt(task));
    }

    @Override
    protected void act(Task task, ChatResponse response) {
        List<String> names = response.getResult().getOutput().getToolCalls().stream()
                .map(ToolCall::name).toList();
        var execution = manager.executeToolCalls(prompt(task), response);
        task.history.clear();
        task.history.addAll(execution.conversationHistory());
        task.steps.add(new Step(task.steps.size() + 1, names));
        if (names.equals(List.of("terminate"))) {
            var result = (org.springframework.ai.chat.messages.ToolResponseMessage) task.history.getLast();
            task.answer = result.getResponses().getFirst().responseData();
        }
    }
}
