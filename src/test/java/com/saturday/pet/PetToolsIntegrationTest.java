package com.saturday.pet;

import com.saturday.pet.app.PetApp;
import com.saturday.pet.tools.FileOperationTool;
import com.saturday.pet.tools.ToolFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.support.ToolCallbacks;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_AI_INTEGRATION_TESTS", matches = "true")
class PetToolsIntegrationTest {
    @Autowired PetApp app;
    @TempDir Path directory;

    @Test
    void modelChoosesAndExecutesFileTool() throws Exception {
        var tools = ToolCallbacks.from(new FileOperationTool(new ToolFiles(directory)));
        String answer = app.doChatWithTools(
                "必须调用 writeFile 工具，创建 pet.txt，内容为 tangyuan。不要仅描述步骤。",
                UUID.randomUUID().toString(), tools);
        assertThat(answer).isNotBlank();
        assertThat(Files.readString(directory.resolve("file/pet.txt"))).contains("tangyuan");
    }
}
