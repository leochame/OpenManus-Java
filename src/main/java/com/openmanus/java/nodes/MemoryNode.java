package com.openmanus.java.nodes;

import com.openmanus.java.omni.memory.ConversationBuffer;
import com.openmanus.java.omni.memory.MemoryTool;
import com.openmanus.java.state.OpenManusAgentState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;

/**
 * The MemoryNode is responsible for managing the agent's memory.
 * It handles the storage and retrieval of both short-term (conversation) and long-term (knowledge) memory.
 * This node ensures that the agent can recall past interactions and learned information to inform its current reasoning process.
 * It may interact with a vector database or other persistent storage solutions.
 */
@Deprecated
//@Component
public class MemoryNode implements AsyncNodeAction<OpenManusAgentState> {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryNode.class);
    
    private final ChatModel chatModel;
    private final MemoryTool memoryTool;
    private final ConversationBuffer conversationBuffer;
    
    // Prompt template for importance evaluation
    private static final PromptTemplate IMPORTANCE_EVALUATION_PROMPT = PromptTemplate.from("""
        Analyze the user's input and the reasoning steps, tool calls, and observations to determine which memories are most important to store.
        
        User Input: {user_input}
        
        Reasoning Steps:
        {{reasoning_steps}}
        
        Tool Results:
        {{tool_results}}
        
        Observations:
        {{observations}}
        
        Based on the analysis, identify the most important memories to store.
        The most important memories are typically:
        1. **User's Current Intent** - Memories that directly relate to the user's current goal or task.
        2. **Recent Reasoning** - Memories that reflect the user's thought process and decision-making.
        3. **Important Facts/Knowledge** - Memories that contain critical information or knowledge.
        4. **Contextual Memories** - Memories that provide background or context for the current situation.
        5. **Important Decisions/Actions** - Memories that record significant decisions or actions taken.
        
        If no important memories are identified, return "NO_IMPORTANT_MEMORY".
        
        Memory Item Format:
        CONTENT: [Memory content]
        TYPE: [FACT/PREFERENCE/EXPERIENCE/CONTEXT/KNOWLEDGE]
        IMPORTANCE: [0.1-1.0]
        TAGS: [comma-separated tags]
        REASON: [Brief explanation for importance]
        
        Example Memory Item:
        CONTENT: [User's current goal is to fix the broken light bulb]
        TYPE: [CONTEXT]
        IMPORTANCE: [0.8]
        TAGS: [light bulb, repair]
        REASON: [Directly related to the user's current task]
        
        If no important memories are found, return "NO_IMPORTANT_MEMORY".
        """);
    
    // Prompt template for memory retrieval
    private static final PromptTemplate MEMORY_RETRIEVAL_PROMPT = PromptTemplate.from("""
        Analyze the user's input and determine if memory retrieval is necessary.
        
        User Input: {user_input}
        
        Current State: {{current_state}}
        
        If memory retrieval is not needed, return "NO_MEMORY_NEEDED".
        
        If memory retrieval is needed, provide a search query.
        
        Search Query: [Search query for memory retrieval]
        PURPOSE: [Purpose of the search]
        
        If memory retrieval is needed, return "SEARCH_QUERY: [search query] PURPOSE: [purpose]".
        """);
    
    public MemoryNode(ChatModel chatModel, MemoryTool memoryTool, ConversationBuffer conversationBuffer) {
        this.chatModel = chatModel;
        this.memoryTool = memoryTool;
        this.conversationBuffer = conversationBuffer;
    }
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(OpenManusAgentState state) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Starting memory management phase - Session ID: {}", state.getSessionId());
                
                // Get conversation context
                String sessionId = state.getSessionId();
                if (sessionId.isEmpty()) {
                    sessionId = "session_" + System.currentTimeMillis();
                }
                
                // Search for relevant memory information
                logger.info("Searching for relevant memory information");
                List<MemoryItem> relevantMemories = searchRelevantMemories(state.getUserInput(), sessionId);
                
                // Build memory context
                Map<String, Object> memoryContext = new HashMap<>();
                memoryContext.put("session_id", sessionId);
                memoryContext.put("relevant_memories", relevantMemories);
                memoryContext.put("memory_count", relevantMemories.size());
                
                // Update conversation buffer
                updateConversationBuffer(state, sessionId);
                
                logger.info("Memory management phase completed");
                
                return Map.of(
                    "current_state", "memory",
                    "memory_context", memoryContext,
                    "reasoning_steps", Map.of("type", "memory", "content", "Memory context updated")
                );
                
            } catch (Exception e) {
                logger.error("Memory node execution failed", e);
                return Map.of(
                    "error", "Memory management failed: " + e.getMessage(),
                    "reasoning_steps", Map.of("type", "error", "content", "Memory failed: " + e.getMessage())
                );
            }
        });
    }
    
    /**
     * Update conversation buffer
     */
    private void updateConversationBuffer(OpenManusAgentState state, String sessionId) {
        try {
            // Add user message
            UserMessage userMessage = UserMessage.from(state.getUserInput());
            conversationBuffer.addMessage(userMessage);
            
            // Add AI message if final answer exists
            String finalAnswer = state.getFinalAnswer();
            if (!finalAnswer.isEmpty()) {
                AiMessage aiMessage = AiMessage.from(finalAnswer);
                conversationBuffer.addMessage(aiMessage);
            }
            
            logger.debug("Conversation buffer updated - Current message count: {}", 
                        conversationBuffer.getStats().getMessageCount());
            
        } catch (Exception e) {
            logger.error("Error updating conversation buffer", e);
        }
    }
    
    /**
     * Search for relevant memories
     */
    private List<MemoryItem> searchRelevantMemories(String userInput, String sessionId) {
        try {
            // Prepare prompt variables
            Map<String, Object> promptVariables = new HashMap<>();
            promptVariables.put("user_input", userInput);
            promptVariables.put("session_id", sessionId);
            
            Prompt prompt = MEMORY_RETRIEVAL_PROMPT.apply(promptVariables);
            
            // Use LLM to retrieve memory
            String retrievalAnalysis = chatModel.chat(prompt.text());
            
            if (retrievalAnalysis.contains("NO_MEMORY_NEEDED")) {
                logger.debug("No memory needed for search");
                return List.of();
            }
            
            // Parse search queries
            List<String> searchQueries = parseSearchQueries(retrievalAnalysis);
            
            List<MemoryItem> retrievedMemories = new ArrayList<>();
            
            // Retrieve memories for each query
            for (String query : searchQueries) {
                String memories = memoryTool.searchMemories(query,3,0.3);
                if (!memories.contains("NO_RELEVANT_MEMORIES")) {
                    retrievedMemories.add(new MemoryItem(memories, "CONTEXT", 0.5, "auto-generated"));
                }
            }
            
            return retrievedMemories;
            
        } catch (Exception e) {
            logger.error("Error retrieving relevant memories", e);
            return List.of();
        }
    }
    
    /**
     * Store important information
     */
    private void storeImportantInformation(OpenManusAgentState state) {
        try {
            // Prepare prompt variables for importance evaluation
            Map<String, Object> promptVariables = new HashMap<>();
            promptVariables.put("user_input", state.getUserInput());
            promptVariables.put("reasoning_steps", formatReasoningSteps(state.getReasoningSteps()));
            promptVariables.put("tool_results", formatToolCalls(state.getToolCalls()));
            promptVariables.put("observations", formatObservations(state.getObservations()));
            
            Prompt prompt = IMPORTANCE_EVALUATION_PROMPT.apply(promptVariables);
            
            // Use LLM to analyze and identify important memories
            String importanceAnalysis = chatModel.chat(prompt.text());
            
            if (importanceAnalysis.contains("NO_IMPORTANT_MEMORY")) {
                logger.debug("No important memory to store");
                return;
            }
            
            // Parse identified memory items
            List<MemoryItem> memoryItems = parseMemoryItems(importanceAnalysis);
            
            for (MemoryItem item : memoryItems) {
                try {
                    String result = memoryTool.storeMemory(
                        item.content, 
                        item.type, 
                        item.importance, 
                        item.tags
                    );
                    logger.info("Storing memory: {} - {}", item.type, item.content.substring(0, Math.min(50, item.content.length())));
                } catch (Exception e) {
                    logger.error("Error storing memory: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.error("Error storing important information", e);
        }
    }
    
    /**
     * Parse search queries from LLM output
     */
    private List<String> parseSearchQueries(String analysis) {
        // Extract the first search query from the analysis
        return List.of(analysis.replaceAll(".*SEARCH_QUERY:\\s*", "").split("\\n")[0]);
    }
    
    /**
     * Parse memory items from LLM output
     */
    private List<MemoryItem> parseMemoryItems(String analysis) {
        // This method is a placeholder and needs to be implemented based on the actual LLM output format
        // For now, it returns a dummy item to avoid compilation errors.
        return List.of(new MemoryItem(
            "Parsed important information", 
            "CONTEXT", 
            0.5, 
            "auto-generated"
        ));
    }
    
    /**
     * Format reasoning steps
     */
    private String formatReasoningSteps(List<Map<String, Object>> steps) {
        return steps.stream()
                .map(step -> step.get("type") + ": " + step.get("content"))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
    
    /**
     * Format tool calls
     */
    private String formatToolCalls(List<Map<String, Object>> toolCalls) {
        return toolCalls.stream()
                .map(call -> call.get("tool_name") + ": " + call.get("result"))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
    
    /**
     * Format observations
     */
    private String formatObservations(List<String> observations) {
        return observations.stream()
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
    
    /**
     * Memory item class
     */
    private static class MemoryItem {
        final String content;
        final String type;
        final double importance;
        final String tags;
        
        MemoryItem(String content, String type, double importance, String tags) {
            this.content = content;
            this.type = type;
            this.importance = importance;
            this.tags = tags;
        }
    }
    
    /**
     * Get buffer statistics
     */
    public ConversationBuffer.BufferStats getBufferStats() {
        return conversationBuffer.getStats();
    }
    
    /**
     * Clear short-term memory
     */
    public void clearShortTermMemory() {
        conversationBuffer.clear();
        logger.info("Short-term memory cleared");
    }
}