package com.saturday.pet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saturday.pet.agent.BaseAgent.Result;
import com.saturday.pet.agent.BaseAgent.Status;
import com.saturday.pet.agent.BaseAgent.Step;
import com.saturday.pet.agent.PetManus;
import com.saturday.pet.agent.PetManus.Draft;
import com.saturday.pet.agent.PetManus.Reference;
import com.saturday.pet.agent.PetManus.WorkbenchResult;
import com.saturday.pet.app.PetImageService;
import com.saturday.pet.app.PetWorkbenchService;
import com.saturday.pet.controller.PetWorkbenchController;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PetWorkbenchApiTest {
    private static final String BASE = "/pet/workbench";
    private static final String CHAT_ID = UUID.randomUUID().toString();
    private static final String OTHER_CHAT_ID = UUID.randomUUID().toString();

    private final PetManus agent = mock(PetManus.class);
    private final PetImageService images = mock(PetImageService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PetWorkbenchController(
            new PetWorkbenchService(agent, images))).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sendsProgressStepsReferencesAndDraftBeforeExplicitPdfConfirmation() throws Exception {
        var step = new Step(1, List.of("proposeDocumentDraft"));
        var draft = new Draft("汤圆一周照料清单", "每天检查饮水并记录喂食。");
        when(agent.runWorkbench(eq("整理一周照料清单"), eq(CHAT_ID), any(), any())).thenAnswer(call -> {
            call.<Consumer<Step>>getArgument(2).accept(step);
            return new WorkbenchResult(new Result(Status.FINISHED, "请查看草稿", List.of(step)), draft,
                    List.of(new Reference("searchPetKnowledge", "日常照料资料")));
        });

        String events = stream(textRequest("整理一周照料清单"));
        assertThat(events).containsSubsequence("event:progress", "event:step", "event:result");
        assertThat(eventData(events, "step").get("tools").get(0).asText()).isEqualTo("proposeDocumentDraft");
        JsonNode result = eventData(events, "result");
        assertThat(result.get("status").asText()).isEqualTo("FINISHED");
        assertThat(result.get("references").get(0).get("content").asText()).isEqualTo("日常照料资料");
        assertThat(result.get("draft").get("content").asText()).isEqualTo(draft.content());
        String actionId = result.get("draft").get("actionId").asText();

        mvc.perform(get(BASE + "/artifacts/{id}", actionId).header("X-Pet-Chat-Id", CHAT_ID))
                .andExpect(status().isNotFound());
        confirm(actionId, OTHER_CHAT_ID).andExpect(status().isNotFound());

        var first = confirm(actionId, CHAT_ID).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(draft.title())).andReturn().getResponse();
        String id = mapper.readTree(first.getContentAsByteArray()).get("id").asText();
        confirm(actionId, CHAT_ID).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));

        mvc.perform(get(BASE + "/artifacts/{id}", id).header("X-Pet-Chat-Id", OTHER_CHAT_ID))
                .andExpect(status().isNotFound());
        var download = mvc.perform(get(BASE + "/artifacts/{id}", id).header("X-Pet-Chat-Id", CHAT_ID))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andReturn().getResponse();
        assertThat(new String(download.getContentAsByteArray(), StandardCharsets.ISO_8859_1)).startsWith("%PDF-");
    }

    @Test
    void sendsImageObservationAndTextFollowupToSameAgentSession() throws Exception {
        var image = new MockMultipartFile("image", "cat.png", "image/png", new byte[]{1});
        when(images.explain(anyString(), eq(image))).thenReturn("照片里是一只橘猫");
        when(agent.runWorkbench(anyString(), eq(CHAT_ID), any(), any())).thenReturn(
                new WorkbenchResult(new Result(Status.FINISHED, "已整理", List.of()), null, List.of()));

        String events = stream(multipart(BASE + "/messages").file(image)
                .param("message", "看照片并整理建议").param("chatId", CHAT_ID));
        assertThat(events).contains("正在查看图片");
        assertThat(eventData(events, "result").has("draft")).isFalse();
        stream(textRequest("改成早晚两项"));

        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(agent, times(2)).runWorkbench(prompt.capture(), eq(CHAT_ID), any(), any());
        assertThat(prompt.getAllValues().get(0)).contains("看照片并整理建议", "照片里是一只橘猫");
        assertThat(prompt.getAllValues().get(1)).isEqualTo("改成早晚两项");
    }

    @Test
    void sendsSafeErrorEventWhenImageValidationFails() throws Exception {
        var image = new MockMultipartFile("image", "cat.gif", "image/gif", new byte[]{1});
        when(images.explain(anyString(), eq(image))).thenThrow(
                new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "private provider details"));

        String events = stream(multipart(BASE + "/messages").file(image)
                .param("message", "看看照片").param("chatId", CHAT_ID));
        assertThat(events).doesNotContain("event:result", "private provider details");
        assertThat(eventData(events, "error").get("message").asText()).contains("JPEG 和 PNG");
        verifyNoInteractions(agent);
    }

    @Test
    void rejectsInvalidSessionIdBeforeStartingStream() throws Exception {
        mvc.perform(post(BASE + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM).content("""
                                {"message":"你好","chatId":"predictable"}
                                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("会话标识无效"));
        verifyNoInteractions(agent, images);
    }

    private MockHttpServletRequestBuilder textRequest(String message) throws Exception {
        return post(BASE + "/messages").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(new PetWorkbenchController.MessageRequest(message, CHAT_ID)));
    }

    private ResultActions confirm(String actionId, String chatId) throws Exception {
        return mvc.perform(post(BASE + "/actions/{id}/confirm", actionId).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(new PetWorkbenchController.ConfirmRequest(chatId))));
    }

    private String stream(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        var pending = mvc.perform(requestBuilder.accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode eventData(String events, String name) throws Exception {
        for (String event : events.split("\n\n")) {
            String prefix = "event:" + name + "\ndata:";
            if (event.startsWith(prefix)) {
                return mapper.readTree(event.substring(prefix.length()));
            }
        }
        throw new AssertionError("Missing SSE event: " + name);
    }
}
