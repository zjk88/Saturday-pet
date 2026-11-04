package com.saturday.pet;

import com.saturday.pet.agent.PetManus;
import com.saturday.pet.agent.BaseAgent;
import com.saturday.pet.app.PetImageService;
import com.saturday.pet.controller.PetAgentController;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class PetAgentImageTest {
    @Test
    void forwardsVisualObservationAndSessionToAgent() throws Exception {
        var vision = mock(PetImageService.class);
        var agent = mock(PetManus.class);
        var image = new MockMultipartFile("image", "cat.png", "image/png", new byte[]{1});
        when(vision.explain(anyString(), eq(image))).thenReturn("一只白色的猫");
        when(agent.run(anyString(), eq("session-a"))).thenReturn(
                new BaseAgent.Result(BaseAgent.Status.FINISHED, "已完成", List.of()));
        var controller = new PetAgentController(agent, vision);
        assertThat(controller.runImage("生成护理清单", "session-a", image).answer()).isEqualTo("已完成");
        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(agent).run(prompt.capture(), eq("session-a"));
        assertThat(prompt.getValue()).contains("生成护理清单", "一只白色的猫");
    }
}
