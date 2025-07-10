package com.openmanus.java.nodes;

import com.openmanus.java.state.OpenManusAgentState;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Observe Node - Observation node in the React framework
 * 
 * Responsible for analyzing action results, evaluating progress, and deciding whether
 * to continue processing or provide a final answer.
 */
@Component
public class ObserveNode implements AsyncNodeAction<OpenManusAgentState> {
    
    private static final Logger logger = LoggerFactory.getLogger(ObserveNode.class);
    
    private final ChatModel chatModel;
    
    // Observation analysis prompt template
    private static final PromptTemplate OBSERVE_PROMPT = PromptTemplate.from("""
        As an intelligent assistant, please analyze the results of the recent action and determine the next reasoning direction.
        
        **Original User Question:**
        {{user_input}}
        
        **Current Reasoning Progress:**
        - Reasoning Step: {{iteration}}/{{max_iterations}}
        - Current State: {{current_state}}
        
        **Recent Reasoning History:**
        {{recent_reasoning}}
        
        **Latest Action Result:**
        {{latest_action_result}}
        
        **All Observations:**
        {{all_observations}}
        
        Please perform a deep analysis based on the above information:
        
        1. **Result Evaluation**: Analyze if the execution of the latest action was effective
        2. **Progress Assessment**: Evaluate if we have gathered enough information to answer the user's question
        3. **Solution Completeness**: Determine the solution progress (0-100%)
        4. **Next Step Decision**: Decide whether to continue reasoning, call more tools, or provide an answer
        
        Please respond strictly in the following format:
        
        **Observation Analysis:**
        [Detailed analysis of action results and current progress]
        
        **Solution Progress:**
        [number]% - [brief explanation]
        
        **Next Step Decision:**
        - Decision: [CONTINUE_THINKING/DIRECT_ANSWER/NEED_REFLECTION/ERROR]
        - Reason: [decision rationale]
        - Suggestion: [what should be done next if continuing reasoning]
        
        **Final Answer (if decision is DIRECT_ANSWER):**
        [Complete answer based on all information]
        """);
    
    @Autowired
    public ObserveNode(ChatModel chatModel) {
        this.chatModel = chatModel;
    }
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(OpenManusAgentState state) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Starting observation phase - Analyzing action results");
                
                // Prepare observation analysis parameters
                Map<String, Object> promptVariables = new HashMap<>();
                promptVariables.put("user_input", state.getUserInput());
                promptVariables.put("iteration", state.getIterationCount());
                promptVariables.put("max_iterations", state.getMaxIterations());
                promptVariables.put("current_state", state.getCurrentState());
                promptVariables.put("recent_reasoning", formatRecentReasoning(state));
                promptVariables.put("latest_action_result", getLatestActionResult(state));
                promptVariables.put("all_observations", formatAllObservations(state));
                
                // Generate observation analysis prompt
                Prompt prompt = OBSERVE_PROMPT.apply(promptVariables);
                
                // Call LLM for observation analysis
                logger.debug("Calling LLM for observation analysis...");
                String observationResult = chatModel.chat(prompt.text());
                
                logger.info("Observation analysis completed, result length: {} characters", observationResult.length());
                
                // Parse observation result
                ObservationDecision decision = parseObservationResult(observationResult);
                
                Map<String, Object> updates = new HashMap<>();
                updates.put("current_state", "observing");
                updates.put("reasoning_steps", Map.of("type", "observation", "content", observationResult));
                
                // Handle different decisions
                switch (decision.decision.toLowerCase()) {
                    case "direct_answer":
                        logger.info("Observation node decided to provide final answer");
                        updates.put("final_answer", decision.finalAnswer);
                        break;
                    case "continue_thinking":
                        logger.info("Observation node decided to continue reasoning");
                        break;
                    case "need_reflection":
                        logger.info("Observation node decided reflection is needed");
                        break;
                    case "error":
                        logger.warn("Observation node detected error: {}", decision.reason);
                        updates.put("error", decision.reason);
                        break;
                    default:
                        logger.warn("Unknown observation decision: {}", decision.decision);
                        updates.put("error", "Unknown observation decision: " + decision.decision);
                }
                
                return updates;
                
            } catch (Exception e) {
                logger.error("Observation node execution failed", e);
                return Map.of(
                    "error", "Observation failed: " + e.getMessage(),
                    "reasoning_steps", Map.of("type", "error", "content", "Observation failed: " + e.getMessage())
                );
            }
        });
    }
    
    /**
     * Format recent reasoning steps
     */
    private String formatRecentReasoning(OpenManusAgentState state) {
        StringBuilder sb = new StringBuilder();
        var steps = state.getReasoningSteps();
        
        if (steps.isEmpty()) {
            return "No reasoning history";
        }
        
        // Show last 5 reasoning steps
        int startIndex = Math.max(0, steps.size() - 5);
        for (int i = startIndex; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            sb.append(String.format("%d. [%s] %s\n", 
                i + 1,
                step.get("type"), 
                truncateText((String) step.get("content"), 150)));
        }
        
        return sb.toString().trim();
    }
    
    /**
     * Get latest action result
     */
    private String getLatestActionResult(OpenManusAgentState state) {
        String result = (String) state.getMetadata().get("last_action_result");
        return result != null ? result : "No action result";
    }
    
    /**
     * Format all observations
     */
    private String formatAllObservations(OpenManusAgentState state) {
        StringBuilder sb = new StringBuilder();
        var observations = state.getObservations();
        
        if (observations.isEmpty()) {
            return "No observation records";
        }
        
        for (int i = 0; i < observations.size(); i++) {
            String obs = observations.get(i);
            sb.append(String.format("%d. %s\n", 
                i + 1, 
                truncateText(obs, 200)));
        }
        
        return sb.toString().trim();
    }
    
    /**
     * Parse observation result
     */
    private ObservationDecision parseObservationResult(String result) {
        ObservationDecision decision = new ObservationDecision();
        
        // Parse solution progress
        var progressMatch = result.matches(".*Solution Progress:.*?(\\d+)%.*");
        if (progressMatch) {
            try {
                decision.solutionProgress = Integer.parseInt(
                    result.replaceAll(".*Solution Progress:.*?(\\d+)%.*", "$1"));
            } catch (NumberFormatException e) {
                decision.solutionProgress = 50; // Default value
            }
        }
        
        // Parse decision
        if (result.toLowerCase().contains("decision: direct_answer") || 
            result.toLowerCase().contains("direct_answer")) {
            decision.decision = "direct_answer";
            
            // Extract final answer
            String[] lines = result.split("\n");
            boolean inAnswerSection = false;
            StringBuilder answerBuilder = new StringBuilder();
            
            for (String line : lines) {
                if (line.contains("Final Answer") && line.contains(":")) {
                    inAnswerSection = true;
                    continue;
                }
                if (inAnswerSection) {
                    if (line.trim().startsWith("**") || line.trim().isEmpty()) {
                        break;
                    }
                    answerBuilder.append(line).append("\n");
                }
            }
            
            decision.finalAnswer = answerBuilder.toString().trim();
            
        } else if (result.toLowerCase().contains("continue_thinking")) {
            decision.decision = "continue_thinking";
        } else if (result.toLowerCase().contains("need_reflection")) {
            decision.decision = "need_reflection";
        } else if (result.toLowerCase().contains("error")) {
            decision.decision = "error";
        } else {
            // Auto-decide based on solution progress
            if (decision.solutionProgress >= 80) {
                decision.decision = "direct_answer";
            } else if (decision.solutionProgress < 20) {
                decision.decision = "need_reflection";
            } else {
                decision.decision = "continue_thinking";
            }
        }
        
        // Extract reason and suggestion
        String[] lines = result.split("\n");
        for (String line : lines) {
            if (line.contains("Reason:")) {
                decision.reason = line.substring(line.indexOf("Reason:") + 7).trim();
            }
            if (line.contains("Suggestion:")) {
                decision.suggestion = line.substring(line.indexOf("Suggestion:") + 11).trim();
            }
        }
        
        return decision;
    }
    
    /**
     * Generate answer from observations
     */
    private String generateAnswerFromObservations(OpenManusAgentState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("Based on the following analysis process, I provide the following answer:\n\n");
        
        // Add reasoning process summary
        var observations = state.getObservations();
        if (!observations.isEmpty()) {
            sb.append("**Analysis Process:**\n");
            for (int i = 0; i < Math.min(observations.size(), 3); i++) {
                String obs = observations.get(i);
                sb.append(String.format("- %s\n", 
                    truncateText(obs, 100)));
            }
            sb.append("\n");
        }
        
        // Add tool call results
        var toolCalls = state.getToolCalls();
        if (!toolCalls.isEmpty()) {
            sb.append("**Execution Results:**\n");
            for (Map<String, Object> toolCall : toolCalls) {
                sb.append(String.format("- Using %s: %s\n", 
                    toolCall.get("tool_name"),
                    truncateText((String) toolCall.get("result"), 100)));
            }
            sb.append("\n");
        }
        
        sb.append("**Conclusion:**\n");
        sb.append("Based on the above analysis, I have completed processing your question. Please let me know if you need more detailed information or have other questions.");
        
        return sb.toString();
    }
    
    /**
     * Truncate text
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
    
    /**
     * Observation decision result class
     */
    private static class ObservationDecision {
        String decision = "continue_thinking";
        String reason = "";
        String suggestion = "";
        String finalAnswer = "";
        int solutionProgress = 50;
    }
}