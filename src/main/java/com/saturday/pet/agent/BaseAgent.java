package com.saturday.pet.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.concurrent.CancellationException;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/** 控制单次任务的生命周期，每个请求独立保存执行历史。 */
public abstract class BaseAgent {
    private static final int MAX_STEPS = 20;

    public enum Status { FINISHED, STEP_LIMIT }
    public record Step(int number, List<String> tools) {}
    public record Result(Status status, String answer, List<Step> steps) {}

    protected static class Task {
        final List<Message> history = new ArrayList<>();
        final List<Step> steps = new ArrayList<>();
        String answer;
    }

    protected abstract String instructions();
    protected abstract void step(Task task);

    public Result run(String message) {
        return run(message, step -> {}, () -> false);
    }

    public Result run(String message, Consumer<Step> onStep, BooleanSupplier cancelled) {
        return runWithHistory(message, List.of(), onStep, cancelled);
    }

    protected Result runWithHistory(String message, List<Message> history,
            Consumer<Step> onStep, BooleanSupplier cancelled) {
        Task task = new Task();
        task.history.add(new SystemMessage(instructions()));
        task.history.addAll(history);
        task.history.add(new UserMessage(message));
        for (int i = 0; i < MAX_STEPS; i++) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException("客户端已断开");
            }
            step(task);
            onStep.accept(task.steps.getLast());
            if (task.answer != null) {
                return new Result(Status.FINISHED, task.answer, List.copyOf(task.steps));
            }
        }
        return new Result(Status.STEP_LIMIT, "已达到 20 步上限，任务尚未确认完成。", List.copyOf(task.steps));
    }
}
