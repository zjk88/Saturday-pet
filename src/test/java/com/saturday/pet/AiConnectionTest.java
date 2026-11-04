package com.saturday.pet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
class AiConnectionTest {
    @Autowired ChatClient.Builder builder;

    @Test
    void shouldReceiveRealModelResponse() {
        String answer = builder.build().prompt()
                .user("计算1+1，只输出阿拉伯数字答案").call().content();
        assertThat(answer).isNotBlank().contains("2");
        System.out.println("模型回复：" + answer);
    }
}
