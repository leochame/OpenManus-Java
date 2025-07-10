package com.openmanus.java.nodes;

import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.openmanus.java.state.OpenManusAgentState;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;

/**
 * Reflection Node - Responsible for analyzing Agent's reasoning process and providing improvement suggestions
 * 
 * Reflection mechanism includes:
 * - Analyzing the effectiveness of reasoning steps
 * - Identifying potential errors or areas for improvement
 * - Generating reflections and suggestions
 * - Deciding if strategy adjustments are needed
 */
@Component
public class ReflectNode implements AsyncNodeAction<OpenManusAgentState> {
    
    private static final Logger logger = LoggerFactory.getLogger(ReflectNode.class);
    
    @Autowired
    private ChatModel chatModel;
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(OpenManusAgentState state) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Starting reflection phase - Iteration: {}", state.getIterationCount());
                
                // Build reflection prompt
                String reflectionPrompt = buildReflectionPrompt(state);
                
                // Call LLM for reflection
                Prompt prompt = Prompt.from(reflectionPrompt);
                String reflection = chatModel.chat(prompt.text());
                
                logger.debug("Reflection result: {}", reflection);
                
                // Return reflection updates
                Map<String, Object> updates = new HashMap<>();
                updates.put("current_state", "reflecting");
                updates.put("reflections", reflection);
                updates.put("reasoning_steps", Map.of(
                    "type", "reflection", 
                    "content", reflection
                ));
                
                return updates;
                
            } catch (Exception e) {
                logger.error("Reflection node execution failed", e);
                return Map.of(
                    "error", "Reflection failed: " + e.getMessage(),
                    "reasoning_steps", Map.of("type", "error", "content", "Reflection failed: " + e.getMessage())
                );
            }
        });
    }
    
    /**
     * Build reflection prompt
     */
    private String buildReflectionPrompt(OpenManusAgentState state) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Please reflect and analyze the following AI Agent's reasoning process:\n\n");
        
        // Add user input
        prompt.append("=== Original Task ===\n");
        prompt.append(state.getUserInput()).append("\n\n");
        
        // Add current reasoning state
        prompt.append("=== Current Reasoning State ===\n");
        prompt.append("Iteration count: ").append(state.getIterationCount()).append("\n");
        prompt.append("Current step: ").append(state.getCurrentStep()).append("\n");
        prompt.append("Latest thought: ").append(state.getCurrentThought()).append("\n\n");
        
        // Add tool call history
        if (!state.getToolCalls().isEmpty()) {
            prompt.append("=== Tool Call History ===\n");
            state.getToolCalls().forEach(toolCall -> {
                prompt.append("- ").append(toolCall.toString()).append("\n");
            });
            prompt.append("\n");
        }
        
        // Add observations
        if (!state.getObservations().isEmpty()) {
            prompt.append("=== Observations ===\n");
            state.getObservations().forEach(obs -> {
                prompt.append("- ").append(obs).append("\n");
            });
            prompt.append("\n");
        }
        
        // Add reflection requirements
        prompt.append("=== Reflection Requirements ===\n");
        prompt.append("Please reflect on the following aspects:\n");
        prompt.append("1. Is the reasoning path logical and efficient?\n");
        prompt.append("2. Are the tools being used appropriately?\n");
        prompt.append("3. Has any important information been missed?\n");
        prompt.append("4. Does the current approach need adjustment?\n");
        prompt.append("5. How can we improve problem-solving efficiency?\n\n");
        
        prompt.append("Please provide specific reflections and improvement suggestions in the following format:\n");
        prompt.append("Reflection: [Your analysis]\n");
        prompt.append("Suggestions: [Improvement suggestions]\n");
        prompt.append("Priority: [High/Medium/Low]\n");
        
        return prompt.toString();
    }
}