package com.saturday.pet.tools;

import java.nio.file.Files;
import org.springframework.ai.tool.annotation.Tool;

/**
 * 文件操作工具：读取和新建 UTF-8 宠物档案。
 * 文件统一保存在 tmp/file，限制文件名和文本大小，写入时不覆盖已有文件。
 * 通过带 @Tool 的 readFile、writeFile 方法向模型提供能力。
 */
public class FileOperationTool {
    private final ToolFiles files;

    public FileOperationTool(ToolFiles files) { this.files = files; }

    @Tool(description = "读取 tmp/file 内已保存的 UTF-8 宠物档案，参数为文件名，不接受路径")
    public String readFile(String fileName) throws Exception {
        var path = files.resolve("file", fileName);
        if (Files.size(path) > 100_000) throw new IllegalArgumentException("文本过大");
        return Files.readString(path);
    }

    // 直接返回真实保存结果，避免模型在转述时省略文件路径。
    @Tool(description = "在 tmp/file 创建 UTF-8 宠物档案，不覆盖已有文件", returnDirect = true)
    public String writeFile(String fileName, String content) throws Exception {
        if (content.length() > 100_000) throw new IllegalArgumentException("文本过大");
        var path = files.resolve("file", fileName);
        Files.writeString(path, content, java.nio.file.StandardOpenOption.CREATE_NEW);
        return "Saved: " + path;
    }
}
