package com.saturday.pet;

import com.saturday.pet.agent.PetManus;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class PetManusMemoryTest {
    @TempDir Path directory;

    @Test
    void remembersAfterDatabaseRestartAndIsolatesSessions() {
        String url = "jdbc:hsqldb:file:" + directory.resolve("memory").toAbsolutePath();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        new ResourceDatabasePopulator(new ClassPathResource(
                "org/springframework/ai/chat/memory/repository/jdbc/schema-hsqldb.sql")).execute(jdbc.getDataSource());
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("宠物名是汤圆")))));
        var agent = new PetManus(model, new ToolCallback[0],
                JdbcChatMemoryRepository.builder().jdbcTemplate(jdbc).build());
        String id = java.util.UUID.randomUUID().toString();
        agent.run("我的宠物叫汤圆", id);
        jdbc.execute("SHUTDOWN");
        var restartedJdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        var restarted = new PetManus(model, new ToolCallback[0],
                JdbcChatMemoryRepository.builder().jdbcTemplate(restartedJdbc).build());
        restarted.run("它叫什么？", id);
        restarted.run("你好", "another-session");
        var prompts = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(3)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(1).getInstructions().toString()).contains("我的宠物叫汤圆");
        assertThat(prompts.getAllValues().get(2).getInstructions().toString()).doesNotContain("我的宠物叫汤圆");
        restartedJdbc.execute("SHUTDOWN");
    }
}
