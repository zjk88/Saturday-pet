package com.saturday.pet.tools;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.tool.annotation.Tool;

/**
 * 终端操作工具：执行固定的运行环境诊断操作。
 * 仅支持 java-version 和 current-time，不执行任意 Shell 命令或脚本。
 * 通过带 @Tool 的 executeTerminalCommand 方法向模型提供能力。
 */
public class TerminalOperationTool {
    @Tool(description = "执行固定终端诊断命令，仅支持 java-version 和 current-time，不支持任意脚本或 shell")
    public String executeTerminalCommand(String command) throws Exception {
        List<String> args = switch (command) {
            case "java-version" -> List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-version");
            case "current-time" -> null;
            default -> throw new IllegalArgumentException("只支持 java-version 或 current-time");
        };
        if (args == null) return java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
        var process = new ProcessBuilder(args).redirectErrorStream(true).start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("命令超时");
        }
        try (var input = process.getInputStream()) {
            return "exit=" + process.exitValue() + "\n" + new String(input.readNBytes(16000), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
