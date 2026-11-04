package com.saturday.pet.app;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PetMemoryKeyTest {
    @Test
    void preservesExistingKeysAndFitsScopedBrowserIdsInJdbcColumn() {
        String id = "c3f3acc5-eeb0-4e29-b0c0-3808fc8af279";
        assertThat(PetApp.memoryKey(id)).isEqualTo(id);
        assertThat(PetApp.memoryKey("mcp:old")).isEqualTo("mcp:old");
        String key = PetApp.memoryKey("mcp:" + id);
        assertThat(key).hasSize(36).isEqualTo(PetApp.memoryKey("mcp:" + id))
                .isNotEqualTo(PetApp.memoryKey("rag:" + id)).isNotEqualTo(id);
    }
}
