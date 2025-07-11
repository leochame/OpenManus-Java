package com.openmanus.java.config;
import com.openmanus.java.tool.BrowserTool;
import com.openmanus.java.tool.FileTool;
import com.openmanus.java.tool.PythonTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Arrays;
import java.util.List;

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
    
    @Autowired
    private OpenManusProperties properties;

    /**
     * Creates a list of tool specifications from all available tool beans.
     * This list can be injected into any component that needs to know about the available tools.
     *
     * @param pythonTool  The Python execution tool.
     * @param fileTool    The file system interaction tool.
     * @param browserTool The web browsing tool.
     * @return A list of {@link ToolSpecification}s.
     */
    @Bean
    public List<ToolSpecification> toolSpecifications(PythonTool pythonTool, FileTool fileTool, BrowserTool browserTool) {
        logger.info("Creating tool specifications from available tools...");
        // Explicitly passing tool instances as an array of objects to avoid ambiguity
        return ToolSpecifications.toolSpecificationsFrom(new Object[]{pythonTool, fileTool, browserTool});
    }
    
    /**
     * Configures the ChatModel bean.
     * This model will be pre-configured with all available tool specifications.
     * Supports OpenAI, Anthropic, and other LLM providers.
     * @return A configured {@link ChatModel}.
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
                default:
                    throw new IllegalArgumentException("Unsupported LLM API type: " + llmConfig.getApiType());
            }
        } catch (Exception e) {
            logger.error("Failed to initialize ChatModel", e);
            throw new RuntimeException("Failed to initialize ChatModel", e);
        }
    }

    private ChatModel createOpenAIChatModel(OpenManusProperties.LlmConfig.DefaultLLM llmConfig) {
        logger.info("Creating OpenAI ChatModel.");
        return OpenAiChatModel.builder()
                .apiKey(llmConfig.getApiKey())
                .baseUrl(llmConfig.getBaseUrl())
                .modelName(llmConfig.getModel())
                .temperature(llmConfig.getTemperature())
                .maxTokens(llmConfig.getMaxTokens())
                .timeout(Duration.ofSeconds(llmConfig.getTimeout()))
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
    
    @Bean
    public StateGraph<AgentExecutor.State> agentExecutorStateGraph(ChatModel chatModel,
                                                                   PythonTool pythonTool,
                                                                   FileTool fileTool,
                                                                   BrowserTool browserTool) throws GraphStateException {
        logger.info("Building the AgentExecutor graph definition...");
        return AgentExecutor.builder()
                .chatModel(chatModel)
                .toolsFromObject(pythonTool)
                .toolsFromObject(fileTool)
                .toolsFromObject(browserTool)
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