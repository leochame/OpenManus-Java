package com.openmanus.java.agent;

import com.openmanus.java.nodes.ActNode;
import com.openmanus.java.nodes.ObserveNode;
import com.openmanus.java.nodes.ThinkNode;
import com.openmanus.java.state.OpenManusAgentState;
import com.openmanus.java.tool.BrowserTool;
import com.openmanus.java.tool.FileTool;
import com.openmanus.java.tool.PythonTool;
import com.openmanus.java.tool.ReflectionTool;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * Intelligent Agent implementation based on LangGraph4j StateGraph architecture
 * Supports complete ReAct reasoning cycle and reflection mechanism
 */
@Component
public class ManusAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(ManusAgent.class);
    
    private final ChatModel chatModel;
    private final PythonTool pythonTool;
    private final FileTool fileTool;
    private final BrowserTool browserTool;
    private final ReflectionTool reflectionTool;
    private final ThinkNode thinkNode;
    private final ActNode actNode;
    private final ObserveNode observeNode;
    private final CompiledGraph<OpenManusAgentState> compiledGraph;
    
    @Autowired
    public ManusAgent(@Qualifier("chatModel") ChatModel chatModel,
                     PythonTool pythonTool,
                     FileTool fileTool,
                     BrowserTool browserTool,
                     ReflectionTool reflectionTool,
                     ThinkNode thinkNode,
                     ActNode actNode,
                     ObserveNode observeNode) {
        this.chatModel = chatModel;
        this.pythonTool = pythonTool;
        this.fileTool = fileTool;
        this.browserTool = browserTool;
        this.reflectionTool = reflectionTool;
        this.thinkNode = thinkNode;
        this.actNode = actNode;
        this.observeNode = observeNode;
        
        // Build StateGraph workflow
        this.compiledGraph = buildStateGraph();
        
        logger.info("ManusAgent initialized with StateGraph architecture using ChatModel: {}", 
                   chatModel.getClass().getSimpleName());
    }
    
    /**
     * Build ReAct workflow based on StateGraph
     */
    private CompiledGraph<OpenManusAgentState> buildStateGraph() {
        try {
            // Define conditional edges: determine next step based on state
            AsyncEdgeAction<OpenManusAgentState> routeNext = edge_async(state -> {
                // Check if max iterations reached
                if (state.isMaxIterationsReached()) {
                    logger.warn("Reached maximum reasoning iterations: {}", state.getMaxIterations());
                    return END;
                }
                
                // Priority routing based on next_action in metadata
                String nextAction = (String) state.getMetadata().get("next_action");
                if (nextAction != null && !nextAction.isEmpty()) {
                    logger.info("Detected next_action: {}", nextAction);
                    switch (nextAction) {
                        case "direct_answer":
                            // Enter act node to handle direct answer
                            logger.info("Direct answer, ending workflow");
                            return END;
                        case "tool_call_python":
                        case "tool_call_file":
                        case "tool_call_web":
                            logger.info("Tool call, entering act node");
                            return "act";
                        case "need_info":
                            logger.info("Need more information, ending with request");
                            return END;
                        case "continue_thinking":
                            logger.info("Continue thinking");
                            return "think";
                        default:
                            logger.warn("Unknown next_action: {}, ending workflow", nextAction);
                            return END;
                    }
                }
                // Fallback based on current state
                String currentState = state.getCurrentState();
                switch (currentState) {
                    case "start":
                    case "thinking":
                        return "think";
                    case "acting": 
                        return "act";
                    case "observing":
                        return "observe";
                    case "reflecting":
                        return "reflect";
                    default:
                        return "think"; // Default to thinking state
                }
            });
            
            // Build StateGraph (using simplified construction method)
            StateGraph<OpenManusAgentState> graph = new StateGraph<OpenManusAgentState>(
                OpenManusAgentState.SCHEMA, 
                initData -> new OpenManusAgentState(initData)
            )
                // Add nodes
                .addNode("think", thinkNode)
                .addNode("act", actNode) 
                .addNode("observe", observeNode)
                .addNode("reflect", this::reflectNode)
                
                // Add edges
                .addEdge(START, "think")  // Start with thinking state
                .addConditionalEdges("think", routeNext, Map.of(
                    "think", "think",
                    "act", "act", 
                    "observe", "observe",
                    "reflect", "reflect",
                    END, END
                ))
                .addConditionalEdges("act", routeNext, Map.of(
                    "think", "think",
                    "act", "act",
                    "observe", "observe", 
                    "reflect", "reflect",
                    END, END
                ))
                .addConditionalEdges("observe", routeNext, Map.of(
                    "think", "think",
                    "act", "act",
                    "observe", "observe",
                    "reflect", "reflect", 
                    END, END
                ))
                .addConditionalEdges("reflect", routeNext, Map.of(
                    "think", "think",
                    "act", "act",
                    "observe", "observe",
                    "reflect", "reflect",
                    END, END
                ));
            
            return graph.compile();
            
        } catch (Exception e) {
            logger.error("Failed to build StateGraph", e);
            throw new RuntimeException("Failed to build StateGraph", e);
        }
    }
    
    /**
     * Reflection node implementation
     */
    private CompletableFuture<Map<String, Object>> reflectNode(OpenManusAgentState state) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Starting reflection phase...");
                
                String taskId = state.getTaskId();
                if (taskId.isEmpty()) {
                    taskId = "task_" + System.currentTimeMillis();
                }
                
                // Record task to reflection tool
                String reasoningSteps = state.getReasoningSteps().toString();
                String currentAnswer = state.getFinalAnswer();
                if (currentAnswer.isEmpty()) {
                    currentAnswer = "Reasoning in progress...";
                }
                
                reflectionTool.recordTask(taskId, state.getUserInput(), 
                                        reasoningSteps, "ReAct reasoning", currentAnswer);
                
                // Perform reflection
                String reflection = reflectionTool.reflectOnTask(taskId);
                
                logger.info("Reflection completed: {}", reflection);
                
                Map<String, Object> result = new HashMap<>();
                result.put("reflections", reflection);
                result.put("current_state", "thinking"); // Continue thinking after reflection
                result.put("task_id", taskId);
                result.put("metadata", Map.of("next_action", "continue_thinking"));
                return result;
                
            } catch (Exception e) {
                logger.error("Reflection node execution failed", e);
                return Map.of(
                    "error", "Error occurred during reflection: " + e.getMessage(),
                    "current_state", "error"
                );
            }
        });
    }
    
    /**
     * Chat with Agent (using StateGraph architecture)
     */
    public Map<String, Object> chatWithCot(String userMessage) {
        logger.info("Starting to process user message: {}", userMessage);
        
        try {
            // Check input
            if (userMessage == null) {
                userMessage = "";
            }
            
            // Create initial state
            Map<String, Object> initialData = new HashMap<>();
            initialData.put("user_input", userMessage);
            initialData.put("session_id", "session_" + System.currentTimeMillis());
            initialData.put("task_id", "task_" + System.currentTimeMillis());
            initialData.put("current_state", "start");
            initialData.put("max_iterations", 10);
            
            // Execute StateGraph workflow
            logger.info("Starting StateGraph reasoning workflow...");
            OpenManusAgentState finalState = null;
            
            // Stream execution of graph and get final state
            for (var nodeOutput : compiledGraph.stream(initialData)) {
                finalState = nodeOutput.state();
                logger.debug("Node execution completed: {}, current state: {}", 
                           nodeOutput.node(), finalState.getCurrentState());
            }
            
            if (finalState == null) {
                throw new RuntimeException("StateGraph execution did not produce final state");
            }
            
            // Build return result
            Map<String, Object> result = new HashMap<>();
            
            String finalAnswer = finalState.getFinalAnswer();
            if (finalAnswer.isEmpty()) {
                String lastThought = finalState.getCurrentThought();
                logger.info("Last thought from final state: {}", lastThought); // DEBUGGING LINE
                if (lastThought != null && lastThought.contains("DIRECT_ANSWER:")) {
                    finalAnswer = lastThought.substring(lastThought.indexOf("DIRECT_ANSWER:") + "DIRECT_ANSWER:".length()).trim();
                }
            }
            
            if (finalAnswer.isEmpty() && finalState.hasError()) {
                finalAnswer = "Sorry, an error occurred while processing your question: " + finalState.getError();
            } else if (finalAnswer.isEmpty()) {
                // If finalAnswer is empty, try to extract from think node's direct answer
                String lastThought = finalState.getCurrentThought();
                if (lastThought != null && lastThought.contains("DIRECT_ANSWER:")) {
                    finalAnswer = lastThought.substring(lastThought.indexOf("DIRECT_ANSWER:") + "DIRECT_ANSWER:".length()).trim();
                } else {
                    finalAnswer = "Reasoning process completed, but unable to provide a clear answer.";
                }
            }
            
            result.put("answer", finalAnswer);
            result.put("content", finalAnswer);
            result.put("cot", extractCotSteps(finalState));
            result.put("reasoning_steps", finalState.getReasoningSteps());
            result.put("tool_calls", finalState.getToolCalls());
            result.put("observations", finalState.getObservations());
            result.put("reflections", finalState.getReflections());
            result.put("iteration_count", finalState.getIterationCount());
            
            logger.info("Reasoning completed, iteration count: {}, final answer: {}", 
                       finalState.getIterationCount(), finalAnswer);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error occurred while processing user message", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("answer", "Sorry, an error occurred while processing your question: " + e.getMessage());
            errorResult.put("content", errorResult.get("answer"));
            errorResult.put("cot", new String[]{"Error: " + e.getMessage()});
            return errorResult;
        }
    }
    
    /**
     * Extract COT steps from final state
     */
    private String[] extractCotSteps(OpenManusAgentState finalState) {
        return finalState.getReasoningSteps().stream()
                .map(step -> step.get("type") + ": " + step.get("content"))
                .toArray(String[]::new);
    }
    
    /**
     * Get Agent status information
     */
    public Map<String, Object> getAgentInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("architecture", "LangGraph4j StateGraph");
        info.put("model", chatModel.getClass().getSimpleName());
        info.put("nodes", new String[]{"think", "act", "observe", "reflect"});
        info.put("tools", new String[]{"PythonTool", "FileTool", "BrowserTool", "ReflectionTool"});
        return info;
    }
}