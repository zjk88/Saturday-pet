package com.saturday.pet;

import com.saturday.pet.agent.BaseAgent.Status;
import com.saturday.pet.agent.PetManus;
import com.saturday.pet.tools.FileOperationTool;
import com.saturday.pet.tools.ToolFiles;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PetManusTest {
    @TempDir Path directory;

    private ChatResponse call(String name, String arguments) {
        var tool = new AssistantMessage.ToolCall(name, "function", name, arguments);
        return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(), List.of(tool)))));
    }

    @Test
    void writesThenReadsAndFinishesUsingToolResults() throws Exception {
        var model = mock(ChatModel.class);
        var agent = new PetManus(model, ToolCallbacks.from(new FileOperationTool(new ToolFiles(directory))));
        when(model.call(any(Prompt.class))).thenReturn(
                call("writeFile", "{\"fileName\":\"pet.txt\",\"content\":\"汤圆\"}"),
                call("readFile", "{\"fileName\":\"pet.txt\"}"))
                .thenAnswer(invocation -> {
                    Prompt prompt = invocation.getArgument(0);
                    var observation = (ToolResponseMessage) prompt.getInstructions().getLast();
                    assertThat(observation.getResponses().getFirst().responseData()).contains("汤圆");
                    return call("terminate", "{\"answer\":\"已核对汤圆档案\"}");
                });
        var result = agent.run("创建档案并回读核对");
        assertThat(result.status()).isEqualTo(Status.FINISHED);
        assertThat(result.answer()).contains("已核对汤圆档案");
        assertThat(result.steps()).hasSize(3);
    }

    public static class MapStub {
        @org.springframework.ai.tool.annotation.Tool(description = "测试地图查询")
        public String findHospitals() {
            return "测试宠物医院，示例路八号";
        }
    }

    @Test
    void usesDiscoveredMapResultToGeneratePdf() throws Exception {
        var model = mock(ChatModel.class);
        var pdf = new com.saturday.pet.tools.PDFGenerationTool(new ToolFiles(directory));
        var agent = new PetManus(model, ToolCallbacks.from(pdf),
                List.of(() -> ToolCallbacks.from(new MapStub())));
        when(model.call(any(Prompt.class))).thenReturn(call("findHospitals", "{}"))
                .thenAnswer(invocation -> {
                    Prompt prompt = invocation.getArgument(0);
                    var result = (ToolResponseMessage) prompt.getInstructions().getLast();
                    String content = result.getResponses().getFirst().responseData();
                    var args = Map.of("fileName", "hospitals.pdf", "content", content);
                    return call("generatePDF", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(args));
                }).thenReturn(call("terminate", "{\"answer\":\"已生成\"}"));
        assertThat(agent.run("查医院并生成 PDF").status()).isEqualTo(Status.FINISHED);
        var path = directory.resolve("pdf/hospitals.pdf");
        try (var doc = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(path.toString()))) {
            assertThat(com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(doc.getPage(1)))
                    .contains("测试宠物医院", "示例路八号");
        }
    }

    @Test
    void stopsAtBudgetWhenModelKeepsCallingTools() throws Exception {
        var model = mock(ChatModel.class);
        var fileTool = new FileOperationTool(new ToolFiles(directory));
        fileTool.writeFile("pet.txt", "汤圆");
        var agent = new PetManus(model, ToolCallbacks.from(fileTool));
        when(model.call(any(Prompt.class))).thenReturn(call("readFile", "{\"fileName\":\"pet.txt\"}"));
        var result = agent.run("读取档案");
        assertThat(result.status()).isEqualTo(Status.STEP_LIMIT);
        assertThat(result.steps()).hasSize(20);
    }
}

