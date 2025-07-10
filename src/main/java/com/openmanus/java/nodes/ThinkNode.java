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
 * Think Node - React framework thinking node
 * 
 * Responsible for analyzing user problems, formulating solution plans, and deciding next actions
 */
@Component
public class ThinkNode implements AsyncNodeAction<OpenManusAgentState> {
    
    private static final Logger logger = LoggerFactory.getLogger(ThinkNode.class);
    
    private final ChatModel chatModel;
    
    // Thinking prompt template
    private static final PromptTemplate THINK_PROMPT = PromptTemplate.from("""
        You are an intelligent assistant using the ReAct (Reasoning and Acting) framework to solve problems.
        
        Current situation:
        - User question: {{user_input}}
        - Reasoning step: {{iteration}}/{{max_iterations}}
        - Previous reasoning: {{previous_reasoning}}
        - Previous observations: {{previous_observations}}
        
        Please conduct deep thinking analysis:
        
        1. **Problem Understanding**: Analyze the core needs and goals of the user's question
        2. **Problem Classification**: Determine the problem type (simple calculation/complex calculation/information query/tool operation, etc.)
        3. **Current Status Assessment**: Evaluate current progress based on available information and observation results
        4. **Solution Strategy**: 
           - For simple problems (such as basic math calculations), provide direct answers
           - For complex problems, decide whether tools are needed
        5. **Tool Selection**: If tools are needed, choose the most appropriate tool
        
        Available tools:
        - executePython: Execute Python code for calculations, data processing, etc.
        - readFile: Read file content
        - writeFile: Write file content
        - listDirectory: List directory contents
        - browseWeb: Browse web pages to get information
        - searchWeb: Search for network information
        - askHuman: Ask user for more information
        
        Please answer in the following format:
        
        **Thinking Analysis:**
        [Detailed thinking process, including problem analysis, strategy formulation, etc.]
        
        **Problem Type:**
        [Simple calculation/Complex calculation/Information query/Tool operation]
        
        **Solution:**
        - Solution type: [Direct answer/Tool call/Need more information]
        - Specific plan: [What specifically to do]
        - Expected result: [What result is expected]
        
        **If direct answer:**
        DIRECT_ANSWER: [Answer content]
        
        **If tool call needed:**
        - Tool name: [Tool name]
        - Parameters: [Specific parameters]
        - Reason: [Why choose this tool]
        """);
    
    @Autowired
    public ThinkNode(ChatModel chatModel) {
        this.chatModel = chatModel;
    }
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(OpenManusAgentState state) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Start thinking phase - User question: {}", state.getUserInput());
                
                // Check if max iterations reached
                if (state.isMaxIterationsReached()) {
                    logger.warn("Max reasoning iterations reached: {}", state.getMaxIterations());
                    Map<String, Object> errorUpdates = new HashMap<>();
                    errorUpdates.put("error", "Max reasoning iterations reached, cannot continue processing");
                    Map<String, Object> errorReasoning = new HashMap<>();
                    errorReasoning.put("type", "error");
                    errorReasoning.put("content", "Max reasoning iterations limit reached");
                    errorUpdates.put("reasoning_steps", errorReasoning);
                    return errorUpdates;
                }
                
                // Prepare parameters for thinking prompt
                Map<String, Object> promptVariables = new HashMap<>();
                promptVariables.put("user_input", state.getUserInput());
                promptVariables.put("iteration", state.getIterationCount());
                promptVariables.put("max_iterations", state.getMaxIterations());
                promptVariables.put("previous_reasoning", formatPreviousReasoning(state));
                promptVariables.put("previous_observations", formatPreviousObservations(state));
                
                // Generate thinking prompt
                Prompt prompt = THINK_PROMPT.apply(promptVariables);
                
                // Call LLM for thinking
                logger.debug("Calling LLM for thinking analysis...");
                String thinkingResult = chatModel.chat(prompt.text());
                
                // Log thinking result
                logger.info("Thinking completed, result length: {} characters", thinkingResult.length());
                
                // Analyze thinking result to determine next action
                String nextAction = analyzeThinkingResult(thinkingResult);
                
                logger.info("Think node completed - Next action: {}", nextAction);
                
                // Return state updates
                Map<String, Object> updates = new HashMap<>();
                updates.put("current_state", "thinking");
                Map<String, Object> reasoningStep = new HashMap<>();
                reasoningStep.put("type", "thinking");
                reasoningStep.put("content", thinkingResult);
                updates.put("reasoning_steps", reasoningStep);
                updates.put("iteration_count", state.getIterationCount() + 1);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("last_think_result", thinkingResult);
                metadata.put("next_action", nextAction);
                updates.put("metadata", metadata);
                return updates;
                
            } catch (Exception e) {
                logger.error("Think node execution failed", e);
                Map<String, Object> errorUpdates = new HashMap<>();
                errorUpdates.put("error", "Error during thinking: " + e.getMessage());
                Map<String, Object> errorReasoning = new HashMap<>();
                errorReasoning.put("type", "error");
                errorReasoning.put("content", "Thinking failed: " + e.getMessage());
                errorUpdates.put("reasoning_steps", errorReasoning);
                return errorUpdates;
            }
        });
    }
    
    /**
     * Format previous reasoning steps
     */
    private String formatPreviousReasoning(OpenManusAgentState state) {
        StringBuilder sb = new StringBuilder();
        var steps = state.getReasoningSteps();
        
        if (steps.isEmpty()) {
            return "No previous reasoning record";
        }
        
        int displayCount = Math.min(steps.size(), 3); // Only show recent 3 steps
        for (int i = steps.size() - displayCount; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            sb.append(String.format("- [%s] %s\n", 
                step.get("type"), 
                truncateText((String) step.get("content"), 200)));
        }
        
        return sb.toString().trim();
    }
    
    /**
     * Format previous observations
     */
    private String formatPreviousObservations(OpenManusAgentState state) {
        StringBuilder sb = new StringBuilder();
        var observations = state.getObservations();
        
        if (observations.isEmpty()) {
            return "No observation record";
        }
        
        int displayCount = Math.min(observations.size(), 2); // Only show recent 2 observations
        for (int i = observations.size() - displayCount; i < observations.size(); i++) {
            String obs = observations.get(i);
            sb.append(String.format("- %s\n", 
                truncateText(obs, 150)));
        }
        
        return sb.toString().trim();
    }
    
    /**
     * Analyze thinking result to determine next action
     */
    private String analyzeThinkingResult(String thinkingResult) {
        String lowerResult = thinkingResult.toLowerCase();
        
        // Check for direct answer
        if (lowerResult.contains("direct_answer:")) {
            return "direct_answer";
        }
        
        // Check if tool call is needed
        if (lowerResult.contains("executepython") || lowerResult.contains("python")) {
            return "tool_call_python";
        } else if (lowerResult.contains("readfile") || lowerResult.contains("read file")) {
            return "tool_call_file";
        } else if (lowerResult.contains("writefile") || lowerResult.contains("write file")) {
            return "tool_call_file";
        } else if (lowerResult.contains("browseweb") || lowerResult.contains("browse web")) {
            return "tool_call_web";
        } else if (lowerResult.contains("searchweb") || lowerResult.contains("search web")) {
            return "tool_call_web";
        } else if (lowerResult.contains("askhuman") || lowerResult.contains("ask human")) {
            return "need_info";
        }
        
        // Default to continue thinking
        return "continue_thinking";
    }
    
    /**
     * Truncate text to specified length
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}