package com.openmanus.java.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * LangChain4j 配置类
 * 
 * 这个类负责配置和创建 LangChain4j 的各种组件，
 * 特别是 ChatLanguageModel，用于与 LLM 进行交互。
 */
@Configuration
public class LangChain4jConfig {
    
    @Autowired
    private OpenManusProperties properties;
    
    /**
     * 创建 ChatLanguageModel Bean
     * 
     * @return 配置好的 ChatLanguageModel 实例
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        OpenManusProperties.LLMSettings.DefaultLLM llmConfig = properties.getLlm().getDefaultLlm();
        
        // 根据 API 类型创建不同的模型
        switch (llmConfig.getApiType().toLowerCase()) {
            case "openai":
                return createOpenAiModel(llmConfig);
            case "azure":
                return createAzureOpenAiModel(llmConfig);
            case "dashscope":
                return createDashScopeModel(llmConfig);
            default:
                throw new IllegalArgumentException("不支持的 LLM API 类型: " + llmConfig.getApiType());
        }
    }
    
    /**
     * 创建 OpenAI 模型
     */
    private ChatLanguageModel createOpenAiModel(OpenManusProperties.LLMSettings.DefaultLLM config) {
        return OpenAiChatModel.builder()
            .apiKey(config.getApiKey())
            .baseUrl(config.getBaseUrl())
            .modelName(config.getModel())
            .temperature(config.getTemperature())
            .maxTokens(config.getMaxTokens())
            .timeout(java.time.Duration.ofSeconds(config.getTimeout()))
            .build();
    }
    
    /**
     * 创建 Azure OpenAI 模型
     */
    private ChatLanguageModel createAzureOpenAiModel(OpenManusProperties.LLMSettings.DefaultLLM config) {
        // Azure OpenAI 配置
        return OpenAiChatModel.builder()
            .apiKey(config.getApiKey())
            .baseUrl(config.getBaseUrl())
            .modelName(config.getModel())
            .temperature(config.getTemperature())
            .maxTokens(config.getMaxTokens())
            .timeout(java.time.Duration.ofSeconds(config.getTimeout()))
            .build();
    }
    
    /**
     * 创建 DashScope (阿里云) 模型
     */
    private ChatLanguageModel createDashScopeModel(OpenManusProperties.LLMSettings.DefaultLLM config) {
        // 注意：这里需要添加 DashScope 的依赖和实现
        // 目前使用 OpenAI 兼容的接口
        return OpenAiChatModel.builder()
            .apiKey(config.getApiKey())
            .baseUrl(config.getBaseUrl())
            .modelName(config.getModel())
            .temperature(config.getTemperature())
            .maxTokens(config.getMaxTokens())
            .timeout(java.time.Duration.ofSeconds(config.getTimeout()))
            .build();
    }
} 