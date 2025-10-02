package com.openmanus.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Python代码执行工具
 * 
 * 提供Python代码的执行能力，支持：
 * 1. 直接执行Python代码字符串
 * 2. 执行Python文件
 * 
 * 设计模式：模板方法模式 - 提取公共执行逻辑
 */
@Component
@Slf4j
public class PythonTool {
    
    private static final String PYTHON_COMMAND = "python3";
    private static final String TEMP_FILE_PREFIX = "python_";
    private static final String TEMP_FILE_SUFFIX = ".py";
    private static final String SUCCESS_PREFIX = "执行成功";
    private static final String SUCCESS_NO_OUTPUT = SUCCESS_PREFIX + ": (无输出)";
    private static final String FAILURE_PREFIX = "执行失败";
    
    /**
     * 执行Python代码字符串
     */
    @Tool("Execute Python code")
    public String executePython(@P("Python code") String code) {
        log.info("执行Python代码: {}", code.length() > 100 ? code.substring(0, 100) + "..." : code);
        
        Path tempFile = null;
        try {
            // 创建临时文件
            tempFile = createTempFile(code);
            
            // 执行Python代码
            return executePythonInternal(tempFile.toString());
            
        } catch (IOException e) {
            log.error("Python代码执行失败", e);
            return FAILURE_PREFIX + ": " + e.getMessage();
        } finally {
            // 清理临时文件
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("清理临时文件失败: {}", tempFile, e);
                }
            }
        }
    }
    
    /**
     * 执行Python文件
     */
    @Tool("Execute Python file")
    public String executePythonFile(@P("Python file path") String filePath) {
        log.info("执行Python文件: {}", filePath);
        
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return "文件不存在: " + filePath;
            }
            
            return executePythonInternal(filePath);
            
        } catch (Exception e) {
            log.error("Python文件执行失败: {}", filePath, e);
            return FAILURE_PREFIX + ": " + e.getMessage();
        }
    }
    
    /**
     * 创建临时Python文件
     * 
     * @param code Python代码内容
     * @return 临时文件路径
     */
    private Path createTempFile(String code) throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");
        String fileName = TEMP_FILE_PREFIX + UUID.randomUUID().toString().substring(0, 8) + TEMP_FILE_SUFFIX;
        Path filePath = Paths.get(tempDir, fileName);
        
        Files.write(filePath, code.getBytes(StandardCharsets.UTF_8));
        return filePath;
    }
    
    /**
     * 内部执行方法 - 模板方法模式
     * 
     * @param pythonFilePath Python文件路径
     * @return 执行结果
     */
    private String executePythonInternal(String pythonFilePath) {
        try {
            // 构建进程
            ProcessBuilder processBuilder = new ProcessBuilder(PYTHON_COMMAND, pythonFilePath);
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // 读取输出
            String output = readProcessOutput(process);
            
            // 等待进程完成
            int exitCode = process.waitFor();
            
            // 格式化返回结果
            return formatExecutionResult(exitCode, output);
            
        } catch (IOException | InterruptedException e) {
            log.error("Python执行失败", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return FAILURE_PREFIX + ": " + e.getMessage();
        }
    }
    
    /**
     * 读取进程输出
     */
    private String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }
    
    /**
     * 格式化执行结果
     */
    private String formatExecutionResult(int exitCode, String output) {
        if (exitCode == 0) {
            if (output.trim().isEmpty()) {
                return SUCCESS_NO_OUTPUT;
            }
            return SUCCESS_PREFIX + ":\n" + output;
        } else {
            return FAILURE_PREFIX + " (退出码: " + exitCode + "):\n" + output;
        }
    }
}