package com.openmanus.java.config;

import com.openmanus.java.llm.LlmClient;
import com.openmanus.java.model.Memory;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot配置类，用于创建LLM相关的Bean
 */
@Configuration
public class LlmClientConfig {

    @Autowired
    private OpenManusProperties properties;

    @Bean
    public LlmClient llmClient(ChatModel chatModel) {
        return new LlmClient(chatModel, properties.getLlm().getDefaultLlm());
    }

    @Bean
    public Memory memory() {
        return new Memory();
    }
}
