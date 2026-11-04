package com.saturday.pet.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class PetDocumentLoader {
    public List<Document> load() throws IOException {
        List<Document> documents = new ArrayList<>();
        var resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:knowledge/*.md");
        for (var resource : resources) {
            var config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withIncludeCodeBlock(false)
                    .withAdditionalMetadata("filename", resource.getFilename())
                    .build();
            documents.addAll(new MarkdownDocumentReader(resource, config).get());
        }
        if (documents.isEmpty()) {
            throw new IllegalStateException("本地知识库没有 Markdown 文档");
        }
        return documents;
    }
}
