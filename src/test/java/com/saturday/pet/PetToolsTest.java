package com.saturday.pet;

import com.saturday.pet.tools.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.support.ToolCallbacks;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetToolsTest {
    @TempDir Path directory;

    @Test
    void writesReadsAndRejectsTraversalOrOverwrite() throws Exception {
        var tool = new FileOperationTool(new ToolFiles(directory));
        tool.writeFile("pet.txt", "汤圆是一只猫");
        assertThat(tool.readFile("pet.txt")).contains("汤圆");
        assertThatThrownBy(() -> tool.readFile("../application.yml")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.writeFile("pet.txt", "overwrite")).isInstanceOf(FileAlreadyExistsException.class);
    }

    @Test
    void searchesAndLimitsResultsWithoutLeakingExtraFields() throws Exception {
        var http = mock(ToolHttp.class);
        when(http.get(anyString())).thenReturn("{\"organic_results\":[{\"title\":\"Pet\",\"link\":\"https://example.com\",\"snippet\":\"Care\",\"extra\":\"hidden\"}]}".getBytes());
        assertThat(new WebSearchTool("test-key", http).searchWeb("pet")).contains("Pet", "Care").doesNotContain("hidden");
        assertThat(new WebSearchTool("", http).searchWeb("pet")).contains("SEARCH_API_KEY");
    }

    @Test
    void scrapesTextAndDownloadsBytes() throws Exception {
        var http = mock(ToolHttp.class);
        when(http.get("https://example.com")).thenReturn("<html><body><script>bad()</script><p>Pet care</p></body></html>".getBytes());
        assertThat(new WebScrapingTool(http).scrapeWebPage("https://example.com")).isEqualTo("Pet care");
        var download = new ResourceDownloadTool(new ToolFiles(directory), http);
        download.downloadResource("https://example.com", "page.html");
        assertThat(Files.readString(directory.resolve("download/page.html"))).contains("Pet care");
        assertThatThrownBy(() -> new ToolHttp().get("https://127.0.0.1/test")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executesAllowedTerminalCommands() throws Exception {
        var tool = new TerminalOperationTool();
        assertThat(tool.executeTerminalCommand("java-version")).contains("exit=0", "version");
        assertThat(tool.executeTerminalCommand("current-time")).contains("Asia/Shanghai");
        assertThatThrownBy(() -> tool.executeTerminalCommand("cmd /c del *")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatesReadableChinesePdf() throws Exception {
        var tool = new PDFGenerationTool(new ToolFiles(directory));
        tool.generatePDF("care.pdf", "宠物护理计划：每天提供清洁饮水。🐱💧");
        try (var pdf = new com.itextpdf.kernel.pdf.PdfDocument(
                new com.itextpdf.kernel.pdf.PdfReader(directory.resolve("pdf/care.pdf").toString()))) {
            assertThat(com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(pdf.getPage(1)))
                    .contains("宠物护理计划");
        }
    }

    @Test
    void springToolCallbackActuallyExecutesFileTool() throws Exception {
        var callbacks = ToolCallbacks.from(new FileOperationTool(new ToolFiles(directory)));
        var write = java.util.Arrays.stream(callbacks)
                .filter(callback -> callback.getToolDefinition().name().equals("writeFile")).findFirst().orElseThrow();
        assertThat(write.getToolMetadata().returnDirect()).isTrue();
        assertThat(write.call("{\"fileName\":\"callback.txt\",\"content\":\"cat\"}")).contains("Saved");
        assertThat(Files.readString(directory.resolve("file/callback.txt"))).isEqualTo("cat");
        assertThat(new ToolRegistration().allTools("")).hasSize(7);
    }
}
