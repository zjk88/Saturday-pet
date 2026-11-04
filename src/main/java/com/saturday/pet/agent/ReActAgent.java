package com.saturday.pet.agent;

import org.springframework.ai.chat.model.ChatResponse;

/** 每轮先请求模型决定下一步，再根据响应执行工具或结束。 */
public abstract class ReActAgent extends BaseAgent {
    protected abstract ChatResponse think(Task task);
    protected abstract void act(Task task, ChatResponse response);

    @Override
    protected final void step(Task task) {
        ChatResponse response = think(task);
        if (response.hasToolCalls()) {
            act(task, response);
        } else {
            String text = response.getResult().getOutput().getText();
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("模型未返回回答或工具调用");
            }
            task.answer = text;
            task.steps.add(new Step(task.steps.size() + 1, java.util.List.of()));
        }
    }
}
