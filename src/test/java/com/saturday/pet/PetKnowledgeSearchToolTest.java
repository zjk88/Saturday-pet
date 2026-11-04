package com.saturday.pet;

import com.saturday.pet.rag.PetKnowledgeBase;
import com.saturday.pet.tools.PetKnowledgeSearchTool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class PetKnowledgeSearchToolTest {
    @Test
    void returnsRetrievedTextAndSourceAndReportsEmptySearch() {
        var knowledge = mock(PetKnowledgeBase.class);
        var store = mock(SimpleVectorStore.class);
        when(knowledge.getStore()).thenReturn(store);
        when(store.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("测试知识片段", Map.of("filename", "pet-care.md"))))
                .thenReturn(List.of());
        var tool = new PetKnowledgeSearchTool(knowledge);
        assertThat(tool.searchPetKnowledge("护理")).contains("测试知识片段", "pet-care.md");
        assertThat(tool.searchPetKnowledge("其他问题")).contains("未检索到", "联网");
    }
}
