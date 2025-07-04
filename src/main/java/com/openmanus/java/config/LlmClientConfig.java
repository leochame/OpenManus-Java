package com.openmanus.java.config;

import com.openmanus.java.llm.LlmClient;
import com.openmanus.java.model.Memory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Spring Boot配置类，用于创建LLM相关的Bean
 */
@Configuration
public class LlmClientConfig {

    @Autowired
    private OpenManusProperties properties;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        OpenManusProperties.LLMSettings.DefaultLLM llmConfig = properties.getLlm().getDefaultLlm();

        return OpenAiChatModel.builder()
                .apiKey(llmConfig.getApiKey())
                .baseUrl(llmConfig.getBaseUrl())
                .modelName(llmConfig.getModel())
                .temperature(llmConfig.getTemperature())
                .maxTokens(llmConfig.getMaxTokens())
                .timeout(Duration.ofSeconds(llmConfig.getTimeout()))
                .build();
    }

    @Bean
    public LlmClient llmClient(ChatLanguageModel chatLanguageModel) {
        return new LlmClient(chatLanguageModel, properties.getLlm().getDefaultLlm());
    }

    @Bean
    public Memory memory() {
        return new Memory();
    }
}
