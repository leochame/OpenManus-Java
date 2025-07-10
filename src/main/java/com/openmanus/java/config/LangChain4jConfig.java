package com.openmanus.java.config;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LangChain4j Configuration
 * 
 * Configures LangChain4j components including ChatModel, EmbeddingModel, etc.
 * Supports multiple LLM providers and embedding models
 */
@Configuration
@EnableConfigurationProperties(OpenManusProperties.class)
public class LangChain4jConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(LangChain4jConfig.class);
    
    private final OpenManusProperties properties;
    
    public LangChain4jConfig(OpenManusProperties properties) {
        this.properties = properties;
    }
    
    /**
     * Configure ChatModel bean
     * Supports OpenAI, Anthropic, and other LLM providers
     */
    @Bean("chatModel")
    public ChatModel chatModel() {
        try {
            logger.info("Initializing ChatModel with configuration: {}", 
                       properties.getLlm().getDefaultLlm().getModel());
            
            var llmConfig = properties.getLlm().getDefaultLlm();
            
            switch (llmConfig.getApiType().toLowerCase()) {
                case "openai":
                    return createOpenAIChatModel(llmConfig);
                case "anthropic":
                    logger.warn("Anthropic not supported yet, using OpenAI as fallback");
                    return createOpenAIChatModel(llmConfig);
                case "qwen":
                    return createQwenChatModel(llmConfig);
                default:
                    logger.warn("Unsupported API type: {}, using OpenAI as default", llmConfig.getApiType());
                    return createOpenAIChatModel(llmConfig);
            }
            
        } catch (Exception e) {
            logger.error("Failed to create ChatModel", e);
            throw new RuntimeException("Failed to initialize ChatModel", e);
        }
    }
    
    /**
     * Create OpenAI ChatModel
     */
    private ChatModel createOpenAIChatModel(OpenManusProperties.LlmConfig.DefaultLLM config) {
        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .build();
    }
    

    
    /**
     * Create Qwen ChatModel (compatible with OpenAI API)
     */
    private ChatModel createQwenChatModel(OpenManusProperties.LlmConfig.DefaultLLM config) {
        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .build();
    }
    
    /**
     * Configure EmbeddingModel bean
     * Currently uses OpenAI embeddings
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        try {
            logger.info("Initializing EmbeddingModel");
            
            var llmConfig = properties.getLlm().getDefaultLlm();
            
            return OpenAiEmbeddingModel.builder()
                    .apiKey(llmConfig.getApiKey())
                    .baseUrl(llmConfig.getBaseUrl())
                    .modelName("text-embedding-ada-002")
                    .timeout(Duration.ofSeconds(llmConfig.getTimeout()))
                    .build();
                    
        } catch (Exception e) {
            logger.error("Failed to create EmbeddingModel", e);
            throw new RuntimeException("Failed to initialize EmbeddingModel", e);
        }
    }
} 