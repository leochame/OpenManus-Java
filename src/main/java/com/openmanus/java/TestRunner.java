package com.openmanus.java;

import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 测试运行器 - 用于测试OpenManus的各个组件
 */
@Component
public class TestRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TestRunner.class);

    @Autowired
    private OpenManusProperties properties;

    @Autowired
    private LlmClient llmClient;

    @Override
    public void run(String... args) throws Exception {
        // 只在明确指定测试模式时运行
        if (args.length > 0 && "test".equals(args[0])) {
            runTests();
        }
    }

    private void runTests() {
        try {
            log.info("🧪 开始 OpenManus Java 测试模式");

            // 使用Spring Boot配置属性
            OpenManusProperties.LLMSettings.DefaultLLM llmSettings = properties.getLlm().getDefaultLlm();
            log.info("配置加载成功 - LLM模型: {}", llmSettings.getModel());
            log.info("API类型: {}", llmSettings.getApiType());
            log.info("Base URL: {}", llmSettings.getBaseUrl());
            log.info("API Key (前10个字符): {}...",
                    llmSettings.getApiKey() != null
                            ? llmSettings.getApiKey().substring(0, Math.min(10, llmSettings.getApiKey().length()))
                            : "null");

            // 测试 1: 工具注册表功能
            log.info("=== 测试工具注册表 ===");

            com.openmanus.java.tool.ToolRegistry toolRegistry = new com.openmanus.java.tool.ToolRegistry(
                    new com.openmanus.java.tool.PythonTool(properties),
                    new com.openmanus.java.tool.FileTool(properties));

            log.info("工具注册表初始化完成，包含 {} 个工具", toolRegistry.getToolCount());
            log.info("可用工具: {}", toolRegistry.getAllTools());

            // 测试Python工具执行
            Object result = toolRegistry.execute("execute",
                    Map.of("code", "print('Hello from Java OpenManus with 阿里云百炼!')"));
            log.info("Python执行结果: {}", result);

            // 测试 2: LLM客户端
            log.info("\n=== 测试LLM客户端 ===");
            try {
                String testPrompt = "Hello! Please respond with '阿里云百炼在Java OpenManus中工作正常!'";
                String llmResponse = llmClient.ask(testPrompt);
                log.info("LLM响应: {}", llmResponse);
                log.info("✅ 阿里云百炼API正常工作!");
            } catch (Exception e) {
                log.error("❌ LLM客户端测试失败: {}", e.getMessage());
                log.info("注意: 这可能是由于API密钥问题或网络问题导致的");
            }

            log.info("OpenManus Java 测试完成!");

        } catch (Exception e) {
            log.error("运行OpenManus Java测试时出错: {}", e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        // 设置测试模式
        String[] testArgs = { "test" };
        SpringApplication.run(OpenManusApplication.class, testArgs);
    }
}
