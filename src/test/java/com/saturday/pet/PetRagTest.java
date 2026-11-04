package com.saturday.pet;

import com.saturday.pet.app.PetApp;
import com.saturday.pet.rag.PetDocumentLoader;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;

class PetRagTest {
    @Test
    void readsMarkdownAndSourceMetadata() throws Exception {
        var documents = new PetDocumentLoader().load();
        assertThat(documents).isNotEmpty();
        assertThat(documents).allSatisfy(document ->
                assertThat(document.getMetadata()).containsKey("filename"));
        assertThat(documents.toString()).contains("木糖醇", "短鼻犬", "营养");
    }

    @Test
    void retrievesKnowledgeAndAddsItToModelPrompt() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("Saturday Pet 图片上传限制为 5MB。")));
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("图片最大 5MB")))));
        String answer = new PetApp(model).doChatWithRag("图片限制是多少？", "test", store);
        assertThat(answer).contains("5MB");
        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getUserMessage().getText()).contains("5MB");
        var search = org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
        verify(store).similaritySearch(search.capture());
        assertThat(search.getValue().getTopK()).isEqualTo(4);
    }
}
