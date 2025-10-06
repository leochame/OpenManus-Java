package com.openmanus.agent.tool;

import com.openmanus.infra.sandbox.ExecutionResult;
import com.openmanus.infra.sandbox.SandboxClient;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Python 代码执行工具
 * 
 * 功能：
 * 1. 在沙箱环境中安全执行 Python 代码
 * 2. 支持代码字符串和文件执行
 * 3. 自动超时控制
 * 
 * 设计模式：模板方法模式 + 策略模式（沙箱/本地执行）
 */
@Component
@Slf4j
public class PythonTool {
    
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    
    private final SandboxClient sandboxClient;
    
    @Autowired
    public PythonTool(SandboxClient sandboxClient) {
        this.sandboxClient = sandboxClient;
    }
    
    /**
     * 执行 Python 代码字符串（在沙箱中）
     */
    @Tool("在沙箱中执行Python代码")
    public String executePython(
            @P("思考过程或代码计划的简要说明") String thought,
            @P("要执行的Python代码") String code) {
        log.info("执行 Python 代码，思考: {}", thought);
        log.debug("代码内容: {}", code.length() > 100 ? code.substring(0, 100) + "..." : code);
        
        try {
            // 在沙箱中直接执行代码
            ExecutionResult result = sandboxClient.executePython(code, DEFAULT_TIMEOUT_SECONDS);
            return formatExecutionResult(result);
        } catch (Exception e) {
            log.error("Python 代码执行失败", e);
            return "执行失败: " + e.getMessage();
        }
    }
    
    /**
     * 执行 Python 文件（在沙箱中）
     */
    @Tool("执行Python文件")
    public String executePythonFile(@P("Python文件路径") String filePath) {
        log.info("执行 Python 文件: {}", filePath);
        
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return "文件不存在: " + filePath;
            }
            
            // 读取文件内容并在沙箱中执行
            String code = Files.readString(path, StandardCharsets.UTF_8);
            ExecutionResult result = sandboxClient.executePython(code, DEFAULT_TIMEOUT_SECONDS);
            return formatExecutionResult(result);
            
        } catch (Exception e) {
            log.error("Python 文件执行失败: {}", filePath, e);
            return "执行失败: " + e.getMessage();
        }
    }
    
    /**
     * 格式化执行结果
     */
    private String formatExecutionResult(ExecutionResult result) {
        if (result.getExitCode() == 0) {
            String output = result.getStdout().trim();
            if (output.isEmpty()) {
                return "✅ 执行成功 (无输出)";
            }
            return "✅ 执行成功:\n" + output;
        } else {
            StringBuilder error = new StringBuilder();
            error.append("❌ 执行失败 (退出码: ").append(result.getExitCode()).append(")");
            
            if (!result.getStderr().isEmpty()) {
                error.append("\n错误信息:\n").append(result.getStderr());
            }
            if (!result.getStdout().isEmpty()) {
                error.append("\n标准输出:\n").append(result.getStdout());
            }
            
            return error.toString();
        }
    }
}