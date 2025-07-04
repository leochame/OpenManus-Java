package com.openmanus.java.llm;

import com.openmanus.java.model.Message;
import com.openmanus.java.model.ToolCall;
import com.openmanus.java.model.ToolChoice;
import com.openmanus.java.model.Function;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.agent.tool.ToolSpecification;
import com.openmanus.java.config.OpenManusProperties;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Mock implementation of LlmClient for testing purposes
 */
public class MockLlmClient extends LlmClient {
    
    private String mockResponse = "Mock response from LLM";
    
    public MockLlmClient() {
        super(createMockChatLanguageModel(), createMockConfig());
    }
    
    public MockLlmClient(String mockResponse) {
        super(createMockChatLanguageModel(), createMockConfig());
        this.mockResponse = mockResponse;
    }
    
    @Override
    public String ask(List<Message> messages, List<Message> systemMessages, Double temperature) {
        return mockResponse;
    }
    
    @Override
    public CompletableFuture<String> askAsync(List<Message> messages, List<Message> systemMessages, Double temperature) {
        return CompletableFuture.completedFuture(mockResponse);
    }
    
    @Override
    public Message askTool(List<Message> messages, List<Message> systemMessages, 
                          List<ToolSpecification> toolSpecifications, ToolChoice toolChoice) {
        // Check if this looks like an error scenario or multi-tool workflow based on the user message
        boolean isErrorScenario = false;
        boolean isMultiToolWorkflow = false;
        if (messages != null && !messages.isEmpty()) {
            String lastUserMessage = messages.get(messages.size() - 1).getContent().toLowerCase();
            isErrorScenario = (lastUserMessage.contains("invalid") && lastUserMessage.contains("task")) || 
                             lastUserMessage.contains("should cause an error");
            isMultiToolWorkflow = lastUserMessage.contains("create a test file") ||
                                 lastUserMessage.contains("write content to the file") ||
                                 lastUserMessage.contains("read the file content");
        }
        
        // For error scenarios, return a response without tool calls (simulating failure)
        if (isErrorScenario) {
            return Message.assistantMessage("I cannot process this invalid task. This appears to be an error scenario.");
        }
        
        // For multi-tool workflow scenarios, return a proper response with tool calls
        if (isMultiToolWorkflow) {
            Message response = Message.assistantMessage("I'll help you with the multi-tool workflow.");
            
            // Simulate calling the first available tool for multi-tool workflows
            if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
                ToolSpecification firstTool = toolSpecifications.get(0);
                Function toolFunction = new Function(firstTool.name(), "{\"message\":\"Multi-tool workflow executed\"}");
                ToolCall toolCall = new ToolCall("call_multi_" + System.currentTimeMillis(), "function", toolFunction);
                response.setToolCalls(List.of(toolCall));
            }
            
            return response;
        }
        
        // Create a mock response that simulates calling the terminate tool
        Message response = Message.assistantMessage(mockResponse);
        
        // If tools are available and tool choice allows it, simulate a terminate tool call
        if (toolSpecifications != null && !toolSpecifications.isEmpty() && 
            (toolChoice == ToolChoice.AUTO || toolChoice == ToolChoice.REQUIRED)) {
            
            // Find terminate tool
            boolean hasTerminateTool = toolSpecifications.stream()
                .anyMatch(tool -> "terminate".equals(tool.name()));
                
            if (hasTerminateTool) {
                // Create a mock terminate tool call
                Function terminateFunction = new Function("terminate", "{\"message\":\"Test completed\"}");
                ToolCall terminateCall = new ToolCall("call_123", "function", terminateFunction);
                response.setToolCalls(List.of(terminateCall));
            }
        }
        
        return response;
    }

    public void setMockResponse(String response) {
        this.mockResponse = response;
    }
    
    private static ChatLanguageModel createMockChatLanguageModel() {
        // Return null as we override all methods that use it
        return null;
    }
    
    private static OpenManusProperties.LLMSettings.DefaultLLM createMockConfig() {
        OpenManusProperties.LLMSettings.DefaultLLM config = new OpenManusProperties.LLMSettings.DefaultLLM();
        config.setModel("mock-model");
        config.setApiType("mock");
        config.setMaxTokens(1000);
        config.setTemperature(0.7);
        return config;
    }
}