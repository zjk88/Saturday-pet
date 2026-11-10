package com.saturday.pet;

import com.saturday.pet.agent.BaseAgent.Status;
import com.saturday.pet.agent.PetManus;
import com.saturday.pet.tools.FileOperationTool;
import com.saturday.pet.tools.ToolFiles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PetWorkbenchAgentTest {
    @TempDir Path directory;

    @Test
    void remembersTheDraftAndReturnsActualMapResultsWithinTheWorkbench() throws Exception {
        var model = mock(ChatModel.class);
        var agent = new PetManus(model, ToolCallbacks.from(new FileOperationTool(new ToolFiles(directory)),
                new MapTool()));
        when(model.call(any(Prompt.class)))
                .thenReturn(text("独立的旧会话答复"))
                .thenAnswer(invocation -> {
                    Prompt prompt = invocation.getArgument(0);
                    var options = (ToolCallingChatOptions) prompt.getOptions();
                    assertThat(options.getToolCallbacks())
                            .extracting(tool -> tool.getToolDefinition().name())
                            .containsExactlyInAnyOrder("saturday_pet_maps_amap_maps_around_search",
                                    "proposeDocumentDraft", "terminate");
                    assertThat(prompt.getInstructions()).extracting(Message::getText)
                            .doesNotContain("旧会话内容");
                    assertThat(prompt.getInstructions().getFirst().getText()).contains("即使没有提到 PDF");
                    return call("saturday_pet_maps_amap_maps_around_search", "{}");
                })
                .thenReturn(call("proposeDocumentDraft", "{\"title\":\"医院清单\",\"content\":\"示例宠物医院：示例路八号\"}"),
                        call("terminate", "{\"answer\":\"草稿已就绪\"}"))
                .thenAnswer(invocation -> {
                    Prompt prompt = invocation.getArgument(0);
                    assertThat(prompt.getInstructions()).extracting(Message::getText)
                            .anySatisfy(message -> assertThat(message).contains("当前文档草稿", "示例路八号"));
                    return call("proposeDocumentDraft", "{\"title\":\"医院清单\",\"content\":\"地址：示例路八号\"}");
                })
                .thenReturn(text("已缩短草稿"));

        agent.run("旧会话内容", "same-id");
        var steps = new ArrayList<Integer>();
        var first = agent.runWorkbench("查附近宠物医院，整理成清单，先给我看草稿", "same-id",
                step -> steps.add(step.number()), () -> false);
        assertThat(first.result().status()).isEqualTo(Status.FINISHED);
        assertThat(first.result().answer()).isEqualTo("草稿已就绪");
        assertThat(first.draft()).isEqualTo(new PetManus.Draft("医院清单", "示例宠物医院：示例路八号"));
        assertThat(first.references()).containsExactly(new PetManus.Reference("maps_around_search", "示例宠物医院：示例路八号"));
        assertThat(steps).containsExactly(1, 2, 3);

        var revision = agent.runWorkbench("改成一行地址", "same-id", step -> {}, () -> false);
        assertThat(revision.draft()).isEqualTo(new PetManus.Draft("医院清单", "地址：示例路八号"));
        assertThat(revision.references()).isEmpty();
    }

    @Test
    void rejectsHallucinatedWriteToolsBeforeSideEffects() throws Exception {
        var model = mock(ChatModel.class);
        var agent = new PetManus(model, ToolCallbacks.from(new FileOperationTool(new ToolFiles(directory))));
        when(model.call(any(Prompt.class)))
                .thenReturn(call("writeFile", "{\"fileName\":\"pet.txt\",\"content\":\"汤圆\"}"));

        assertThatThrownBy(() -> agent.runWorkbench("保存到文件", "session", step -> {}, () -> false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("不可用的工具");
        assertThat(directory.resolve("file/pet.txt")).doesNotExist();
    }

    private static ChatResponse call(String name, String arguments) {
        var tool = new AssistantMessage.ToolCall(name, "function", name, arguments);
        return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(), List.of(tool)))));
    }

    private static ChatResponse text(String answer) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
    }

    public static class MapTool {
        @Tool(name = "saturday_pet_maps_amap_maps_around_search", description = "测试地图查询")
        public String findHospitals() {
            return "示例宠物医院：示例路八号";
        }
    }
}
