package com.saturday.pet;

import com.saturday.pet.app.PetVisionApp;
import com.saturday.pet.controller.PetVisionController;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;

class PetVisionTest {
    @Test
    void missingVisionKeyDoesNotCallProvider() {
        var app = new PetVisionApp("", "https://api.deepseek.com", "deepseek-flash");
        assertThatThrownBy(() -> app.explain("Describe", new byte[]{1}, "image/png"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("503");
    }

    @Test
    void sendsImageAndOverridesDefaultModel() throws Exception {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("A cat")))));
        var app = new PetVisionApp(model, "deepseek-flash");
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        var controller = new PetVisionController(app);
        assertThat(controller.explain("Describe", new MockMultipartFile(
                "image", "cat.png", "image/png", output.toByteArray()))).isEqualTo("A cat");
        var captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captor.capture());
        assertThat(captor.getValue().getOptions().getModel()).isEqualTo("deepseek-flash");
        assertThat(captor.getValue().getUserMessage().getMedia()).hasSize(1);
    }

    @Test
    void rejectsInvalidFileBeforeCallingModel() {
        var app = mock(PetVisionApp.class);
        var controller = new PetVisionController(app);
        assertThatThrownBy(() -> controller.explain("Describe", new MockMultipartFile(
                "image", "fake.png", "image/png", new byte[]{1,2,3})))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(app);
    }
}
