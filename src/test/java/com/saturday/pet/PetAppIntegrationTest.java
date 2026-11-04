package com.saturday.pet;

import com.saturday.pet.app.PetApp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_AI_INTEGRATION_TESTS", matches = "true")
class PetAppIntegrationTest {
    @Autowired PetApp petApp;

    @Test
    void recallsPetAcrossThreeTurns() {
        String chatId = UUID.randomUUID().toString();
        assertThat(petApp.doChat("你好，我是程序员小明", chatId)).isNotBlank();
        assertThat(petApp.doChat("我的宠物是一只猫，名字叫小红", chatId)).isNotBlank();
        String answer = petApp.doChat("我的宠物叫什么？请回忆刚才的对话", chatId);
        assertThat(answer).isNotBlank().contains("小红");
    }
}
