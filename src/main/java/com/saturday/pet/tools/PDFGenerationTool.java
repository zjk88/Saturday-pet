package com.saturday.pet.tools;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import org.springframework.ai.tool.annotation.Tool;

/**
 * PDF 生成工具：将宠物护理计划、资讯等文本生成为支持中文的 PDF。
 * 先在内存完成生成，成功后保存到 tmp/pdf，避免异常留下损坏的文件。
 * 字体不支持的字符（例如部分 Emoji）替换为空格；不覆盖已有文件。
 */
public class PDFGenerationTool {

    private final ToolFiles files;

    public PDFGenerationTool(ToolFiles files) {
        this.files = files;
    }

    @Tool(description = "把宠物护理计划或资讯文本生成中文 PDF，保存到 tmp/pdf，不覆盖已有文件")
    public String generatePDF(String fileName, String content) throws Exception {
        if (fileName == null || !fileName.endsWith(".pdf") || content == null
                || content.isBlank() || content.length() > 100000) {
            throw new IllegalArgumentException("需要 .pdf 文件名及非空内容，内容不超过十万字");
        }
        var path = files.resolve("pdf", fileName);
        Files.write(path, renderPdf(content), StandardOpenOption.CREATE_NEW);
        return "PDF saved: " + path;
    }

    public static byte[] renderPdf(String content) throws IOException {
        var font = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");
        var supportedText = new StringBuilder();
        content.codePoints().forEach(codePoint -> {
            if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t'
                    || font.containsGlyph(codePoint)) {
                supportedText.appendCodePoint(codePoint);
            } else {
                supportedText.append(' ');
            }
        });

        var buffer = new ByteArrayOutputStream();
        try (var pdf = new PdfDocument(new PdfWriter(buffer));
             var document = new Document(pdf)) {
            document.setFont(font);
            document.add(new Paragraph(supportedText.toString()));
        }
        return buffer.toByteArray();
    }
}
