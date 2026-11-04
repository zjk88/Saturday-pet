package com.saturday.pet.tools;

import com.saturday.pet.rag.PetKnowledgeBase;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/** 检索本地宠物知识片段及来源，供智能体判断是否需要补充联网资料。 */
@Component
public class PetKnowledgeSearchTool {
    private final PetKnowledgeBase knowledgeBase;

    public PetKnowledgeSearchTool(PetKnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @Tool(description = "优先检索本地宠物医疗科普、饲养、护理和品种知识。参数 query 为具体知识问题。返回相关片段及来源；无结果时可考虑联网补充。")
    public String searchPetKnowledge(String query) {
        Assert.hasText(query, "检索问题不能为空");
        var documents = knowledgeBase.getStore().similaritySearch(
                SearchRequest.builder().query(query).topK(4).similarityThreshold(0.5).build());
        if (documents == null || documents.isEmpty()) {
            return "本地知识库未检索到相关资料，可联网补充。";
        }
        var result = new StringBuilder("以下为本地知识片段，请判断能否回答问题，引用时保留来源。\n");
        for (var document : documents) {
            result.append("\n来源：").append(document.getMetadata().getOrDefault("filename", "未提供"))
                    .append("\n").append(document.getText()).append("\n");
        }
        return result.toString();
    }
}
