package com.saturday.pet.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class PetKnowledgeBase {
    private final PetDocumentLoader loader;
    private final EmbeddingModel embedding;
    private volatile SimpleVectorStore store;

    public PetKnowledgeBase(PetDocumentLoader loader,
            @Value("${spring.ai.openai.api-key}") String key,
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${app.ai.embedding-model:text-embedding-v3}") String model) {
        this.loader = loader;
        this.embedding = new OpenAiEmbeddingModel(
                OpenAiApi.builder().apiKey(key).baseUrl(baseUrl).build(),
                org.springframework.ai.document.MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(model).build());
    }

    public synchronized SimpleVectorStore getStore() {
        if (store == null) {
            var candidate = SimpleVectorStore.builder(embedding).build();
            try {
                candidate.add(loader.load());
            } catch (IOException e) {
                throw new IllegalStateException("读取本地知识库失败", e);
            }
            store = candidate;
        }
        return store;
    }
}
