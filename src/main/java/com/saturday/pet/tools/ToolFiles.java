package com.saturday.pet.tools;

import java.nio.file.*;
import java.io.IOException;

/**
 * 文件管理辅助类：统一解析工具输出目录及文件路径。
 * 负责创建目录、校验文件名及拒绝目标目录或文件的符号链接。
 * 供文件读写、下载和 PDF 工具复用；自身不作为模型可调用工具。
 */
public class ToolFiles {
    private final Path root;

    public ToolFiles(Path root) throws IOException {
        Files.createDirectories(root);
        this.root = root.toRealPath();
    }

    public Path resolve(String category, String name) throws IOException {
        if (name == null || !name.matches("[\\p{L}\\p{N}_. -]{1,120}") || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("仅允许不含路径的文件名");
        }
        Path directory = root.resolve(category);
        if (Files.isSymbolicLink(directory)) throw new SecurityException("不允许符号链接");
        Files.createDirectories(directory);
        Path target = directory.resolve(name);
        if (Files.isSymbolicLink(target)) throw new SecurityException("不允许符号链接");
        return target;
    }
}
