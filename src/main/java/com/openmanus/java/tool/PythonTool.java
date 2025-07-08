package com.openmanus.java.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Python 代码执行工具
 * 使用 langchain4j 的 @Tool 注解
 */
@Component
public class PythonTool {
    
    private static final Logger logger = LoggerFactory.getLogger(PythonTool.class);

    @Tool("执行 Python 代码")
    public String executePython(@P("Python 代码") String code) {
        try {
            // 创建临时文件
            String tempDir = System.getProperty("java.io.tmpdir");
            String fileName = "python_" + UUID.randomUUID().toString().substring(0, 8) + ".py";
            Path filePath = Paths.get(tempDir, fileName);
            
            // 写入代码到文件
            Files.write(filePath, code.getBytes());
            
            // 执行 Python 代码
            ProcessBuilder processBuilder = new ProcessBuilder("python3", filePath.toString());
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // 等待进程完成
            int exitCode = process.waitFor();
            
            // 清理临时文件
            Files.deleteIfExists(filePath);
            
            if (exitCode == 0) {
                return "执行成功:\n" + output.toString();
            } else {
                return "执行失败 (退出码: " + exitCode + "):\n" + output.toString();
            }
            
        } catch (IOException | InterruptedException e) {
            logger.error("Python 执行失败", e);
            return "执行失败: " + e.getMessage();
            }
    }
    
    @Tool("执行 Python 文件")
    public String executePythonFile(@P("Python 文件路径") String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return "文件不存在: " + filePath;
                }
            
            // 执行 Python 文件
            ProcessBuilder processBuilder = new ProcessBuilder("python3", filePath);
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // 等待进程完成
            int exitCode = process.waitFor();
        
            if (exitCode == 0) {
                return "执行成功:\n" + output.toString();
        } else {
                return "执行失败 (退出码: " + exitCode + "):\n" + output.toString();
    }
    
        } catch (IOException | InterruptedException e) {
            logger.error("Python 文件执行失败", e);
            return "执行失败: " + e.getMessage();
        }
    }
}