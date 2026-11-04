package com.saturday.pet;

import com.saturday.pet.agent.BaseAgent.Result;
import com.saturday.pet.agent.BaseAgent.Status;
import com.saturday.pet.agent.BaseAgent.Step;
import com.saturday.pet.agent.PetManus;
import com.saturday.pet.app.PetApp;
import com.saturday.pet.controller.PetAgentController;
import com.saturday.pet.controller.PetAppController;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PetStreamingApiTest {
    @Test
    void chatSendsSeparateTextEvents() throws Exception {
        var app = mock(PetApp.class);
        when(app.doChatByStream("你好", "a")).thenReturn(Flux.just("你", "好"));
        var mvc = MockMvcBuilders.standaloneSetup(new PetAppController(app))
                .setMessageConverters(new org.springframework.http.converter.StringHttpMessageConverter(
                        java.nio.charset.StandardCharsets.UTF_8)).build();
        var pending = mvc.perform(get("/pet/chat/sse").param("message", "你好").param("chatId", "a"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
                .andExpect(content().bytes("data:你\n\ndata:好\n\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void agentSendsStepBeforeFinalResult() throws Exception {
        var agent = mock(PetManus.class);
        var step = new Step(1, List.of("readFile"));
        when(agent.run(eq("读取档案"), eq("agent-a"), any(), any())).thenAnswer(call -> {
            call.<Consumer<Step>>getArgument(2).accept(step);
            return new Result(Status.FINISHED, "完成", List.of(step));
        });
        var mvc = MockMvcBuilders.standaloneSetup(new PetAgentController(agent)).build();
        var pending = mvc.perform(get("/pet/agent/run/sse").param("message", "读取档案").param("chatId", "agent-a"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
                .andExpect(content().string(containsString("event:step")))
                .andExpect(content().string(containsString("event:result")))
                .andExpect(content().string(containsString("FINISHED")));
    }
}

