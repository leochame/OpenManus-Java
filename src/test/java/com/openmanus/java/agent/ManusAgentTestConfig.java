package com.openmanus.java.agent;

import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.llm.LlmClient;
import com.openmanus.java.llm.MockLlmClient;
import com.openmanus.java.model.Memory;
import com.openmanus.java.tool.ToolRegistry;
import com.openmanus.java.tool.MockAskHumanTool;
import com.openmanus.java.tool.TerminateTool;
import com.openmanus.java.tool.FileTool;
import com.openmanus.java.tool.PythonTool;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 测试配置类，为测试环境提供特殊的配置
 */
@TestConfiguration
public class ManusAgentTestConfig {

    @Bean
    @Primary
    public OpenManusProperties testProperties() {
        OpenManusProperties properties = new OpenManusProperties();
        
        // 配置LLM设置
        OpenManusProperties.LLMSettings llmSettings = new OpenManusProperties.LLMSettings();
        OpenManusProperties.LLMSettings.DefaultLLM defaultLlm = new OpenManusProperties.LLMSettings.DefaultLLM();
        defaultLlm.setApiType("mock");
        defaultLlm.setModel("test-model");
        defaultLlm.setMaxTokens(1000);
        defaultLlm.setTemperature(0.7);
        llmSettings.setDefaultLlm(defaultLlm);
        properties.setLlm(llmSettings);
        
        // 配置沙盒设置
        OpenManusProperties.SandboxSettings sandboxSettings = new OpenManusProperties.SandboxSettings();
        sandboxSettings.setUseSandbox(false);
        properties.setSandbox(sandboxSettings);
        
        // 配置应用设置 - 使用默认的AppSettings，只修改工作空间路径
        properties.getApp().setWorkspaceRoot(System.getProperty("user.dir") + "/test-workspace");
        
        return properties;
    }

    @Bean
    @Primary
    public LlmClient testLlmClient() {
        // 创建一个会快速终止的MockLlmClient
        return new MockLlmClient("测试完成，任务结束");
    }
    
    /**
     * 创建一个专门用于测试的ManusAgent，启用测试模式
     */
    @Bean
    public ManusAgent testManusAgent(LlmClient llmClient, Memory memory, 
                                   OpenManusProperties properties, ToolRegistry toolRegistry) {
        ManusAgent agent = new ManusAgent(llmClient, memory, properties, toolRegistry,
                com.openmanus.java.model.ToolChoice.AUTO,
                java.util.Set.of("terminate"));
        
        // 启用测试模式，限制执行步数
        agent.enableTestMode();
        
        return agent;
    }

    @Bean
    @Primary
    public Memory testMemory() {
        return new Memory();
    }

    @Bean
    @Primary
    public ToolRegistry testToolRegistry(OpenManusProperties properties) {
        // 使用Mock工具来避免阻塞
        return new ToolRegistry(
            new MockAskHumanTool("测试完成", "任务结束", "继续执行"),
            new TerminateTool(),
            new FileTool(properties)
        );
    }
}