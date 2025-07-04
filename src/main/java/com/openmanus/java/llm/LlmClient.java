package com.openmanus.java.llm;

import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.exception.TokenLimitExceededException;
import com.openmanus.java.model.Message;
import com.openmanus.java.model.ToolCall;
import com.openmanus.java.model.ToolChoice;
import com.openmanus.java.model.Function;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * LLM client that provides a unified interface for interacting with language models.
 * Supports token counting, rate limiting, and async operations.
 * Corresponds to the LLM class in the Python version.
 */
public class LlmClient {
    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    
    private final ChatLanguageModel model;
    private final String modelName;
    private final int maxTokens;
    private final double temperature;
    private final String apiType;
    private final Integer maxInputTokens;
    
    // Token tracking
    private volatile long totalInputTokens = 0;
    private volatile long totalCompletionTokens = 0;
    
    // Multimodal model support
    private static final List<String> MULTIMODAL_MODELS = List.of(
        "gpt-4-vision-preview",
        "gpt-4o",
        "gpt-4o-mini",
        "claude-3-opus-20240229",
        "claude-3-sonnet-20240229",
        "claude-3-haiku-20240307"
    );
    
    public LlmClient(ChatLanguageModel model, OpenManusProperties.LLMSettings.DefaultLLM config) {
        this.model = model;
        this.modelName = config.getModel();
        this.maxTokens = config.getMaxTokens();
        this.temperature = config.getTemperature();
        this.apiType = config.getApiType();
        this.maxInputTokens = config.getMaxTokens(); // 使用maxTokens作为输入限制
        
        log.info("LLM Client initialized with model: {}, API type: {}", modelName, apiType);
    }
    

    
    /**
     * Send a prompt to the LLM and get the response asynchronously.
     * 
     * @param messages List of conversation messages
     * @param systemMessages Optional system messages to prepend
     * @param temperature Optional temperature override
     * @return CompletableFuture containing the response
     */
    public CompletableFuture<String> askAsync(
            List<Message> messages, 
            List<Message> systemMessages, 
            Double temperature) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return ask(messages, systemMessages, temperature);
            } catch (Exception e) {
                log.error("Error in async LLM call: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Send a prompt to the LLM and get the response synchronously.
     * 
     * @param messages List of conversation messages
     * @param systemMessages Optional system messages to prepend
     * @param temperature Optional temperature override
     * @return The response string
     */
    public String ask(List<Message> messages, List<Message> systemMessages, Double temperature) {
        try {
            // Convert messages to langchain4j format
            List<ChatMessage> chatMessages = new ArrayList<>();
            
            // Add system messages first
            if (systemMessages != null) {
                for (Message msg : systemMessages) {
                    chatMessages.add(convertToLangChain4jMessage(msg));
                }
            }
            
            // Add conversation messages
            for (Message msg : messages) {
                chatMessages.add(convertToLangChain4jMessage(msg));
            }
            
            // Estimate input tokens (simplified)
            int estimatedInputTokens = estimateTokens(chatMessages);
            
            // Check token limits
            if (!checkTokenLimit(estimatedInputTokens)) {
                throw new TokenLimitExceededException(
                    String.format("Request may exceed input token limit (Current: %d, Needed: %d, Max: %d)",
                        totalInputTokens, estimatedInputTokens, maxInputTokens)
                );
            }
            
            // Make the API call
            Response<AiMessage> response = model.generate(chatMessages);
            
            // Update token counts
            updateTokenCount(estimatedInputTokens, estimateTokens(response.content().text()));
            
            return response.content().text();
            
        } catch (Exception e) {
            log.error("Error in LLM call: {}", e.getMessage(), e);
            throw new RuntimeException("LLM call failed", e);
        }
    }
    
    /**
     * Simplified version that takes just a user message.
     */
    public String ask(String userMessage) {
        List<Message> messages = List.of(Message.userMessage(userMessage));
        return ask(messages, null, null);
    }
    
    /**
     * Send a prompt to the LLM with tool support and get the response.
     * 
     * @param messages List of conversation messages
     * @param systemMessages Optional system messages to prepend
     * @param toolSpecifications Available tools for the LLM to use
     * @param toolChoice Tool choice strategy
     * @return The response message with potential tool calls
     */
    public Message askTool(List<Message> messages, List<Message> systemMessages, 
                          List<ToolSpecification> toolSpecifications, ToolChoice toolChoice) {
        try {
            // Convert messages to langchain4j format
            List<ChatMessage> chatMessages = new ArrayList<>();
            
            // Add system messages first
            if (systemMessages != null) {
                for (Message msg : systemMessages) {
                    chatMessages.add(convertToLangChain4jMessage(msg));
                }
            }
            
            // Add conversation messages
            for (Message msg : messages) {
                chatMessages.add(convertToLangChain4jMessage(msg));
            }
            
            // Estimate input tokens
            int estimatedInputTokens = estimateTokens(chatMessages);
            
            // Check token limits
            if (!checkTokenLimit(estimatedInputTokens)) {
                throw new TokenLimitExceededException(
                    String.format("Request may exceed input token limit (Current: %d, Needed: %d, Max: %d)",
                        totalInputTokens, estimatedInputTokens, maxInputTokens)
                );
            }
            
            // Make the API call with tool support
            Response<AiMessage> response;
            if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
                // Use tool-enabled generation
                response = model.generate(chatMessages, toolSpecifications);
            } else {
                // Regular generation without tools
                response = model.generate(chatMessages);
            }
            
            // Update token counts
            AiMessage aiMessage = response.content();
            String responseText = aiMessage.text();
            updateTokenCount(estimatedInputTokens, estimateTokens(responseText));
            
            // Convert response to our Message format
            Message resultMessage = Message.assistantMessage(responseText != null ? responseText : "");
            
            // Handle tool calls if present
            if (aiMessage.hasToolExecutionRequests()) {
                List<ToolCall> toolCalls = new ArrayList<>();
                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    Function function = new Function(
                        request.name(),
                        request.arguments()
                    );
                    ToolCall toolCall = new ToolCall(
                        request.id(),
                        "function",
                        function
                    );
                    toolCalls.add(toolCall);
                }
                resultMessage.setToolCalls(toolCalls);
            }
            
            return resultMessage;
            
        } catch (Exception e) {
            log.error("Error in LLM tool call: {}", e.getMessage(), e);
            throw new RuntimeException("LLM tool call failed", e);
        }
    }
    
    private ChatMessage convertToLangChain4jMessage(Message message) {
        switch (message.getRole()) {
            case SYSTEM:
                return SystemMessage.from(message.getContent() != null ? message.getContent() : "");
            case USER:
                return UserMessage.from(message.getContent() != null ? message.getContent() : "");
            case ASSISTANT:
                // Handle assistant messages with potential tool calls
                if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                    List<ToolExecutionRequest> toolRequests = new ArrayList<>();
                    for (ToolCall toolCall : message.getToolCalls()) {
                        ToolExecutionRequest request = ToolExecutionRequest.builder()
                            .id(toolCall.id())
                            .name(toolCall.function().name())
                            .arguments(toolCall.function().arguments())
                            .build();
                        toolRequests.add(request);
                    }
                    // Use static factory method for AiMessage with tool requests
                    return AiMessage.aiMessage(toolRequests.toArray(new ToolExecutionRequest[0]));
                } else {
                    return AiMessage.from(message.getContent() != null ? message.getContent() : "");
                }
            case TOOL:
                // Tool result messages
                if (message.getToolCallId() != null) {
                    return new ToolExecutionResultMessage(
                        message.getToolCallId(),
                        "tool",
                        message.getContent() != null ? message.getContent() : ""
                    );
                } else {
                    // Fallback for tool messages without ID
                    String content = message.getContent() != null ? message.getContent() : "";
                    return UserMessage.from("Tool result: " + content);
                }
            default:
                throw new IllegalArgumentException("Unsupported message role: " + message.getRole());
        }
    }
    
    private int estimateTokens(List<ChatMessage> messages) {
        // Simplified token estimation - in production, use a proper tokenizer
        return messages.stream()
            .mapToInt(msg -> {
                String text = msg.text();
                return text != null ? text.length() / 4 : 0; // Rough estimate: 1 token ≈ 4 characters
            })
            .sum();
    }
    
    private int estimateTokens(String text) {
        return text != null ? text.length() / 4 : 0; // Rough estimate, handle null text
    }
    
    private boolean checkTokenLimit(int inputTokens) {
        if (maxInputTokens == null) {
            return true; // No limit set
        }
        return inputTokens <= maxInputTokens;
    }
    
    private void updateTokenCount(int inputTokens, int completionTokens) {
        this.totalInputTokens += inputTokens;
        this.totalCompletionTokens += completionTokens;
        
        log.info("Token usage: Input={}, Completion={}, Cumulative Input={}, Cumulative Completion={}, Total={}, Cumulative Total={}",
            inputTokens, completionTokens, totalInputTokens, totalCompletionTokens,
            inputTokens + completionTokens, totalInputTokens + totalCompletionTokens);
    }
    
    public boolean supportsImages() {
        return MULTIMODAL_MODELS.contains(modelName);
    }
    
    // Getters
    public String getModelName() {
        return modelName;
    }
    
    public long getTotalInputTokens() {
        return totalInputTokens;
    }
    
    public long getTotalCompletionTokens() {
        return totalCompletionTokens;
    }
    
    public long getTotalTokens() {
        return totalInputTokens + totalCompletionTokens;
    }
    
    /**
     * Reset token counters - useful for starting fresh conversations
     */
    public void resetTokenCount() {
        this.totalInputTokens = 0;
        this.totalCompletionTokens = 0;
        log.info("Token counters reset");
    }
    
    /**
     * Check if we should reset context due to high token usage
     */
    public boolean shouldResetContext() {
        return totalInputTokens > (maxInputTokens * 0.8); // Reset when 80% of limit reached
    }
}