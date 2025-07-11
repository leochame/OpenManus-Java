package com.openmanus.java.workflow;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openmanus.java.state.OpenManusAgentState;
import com.openmanus.java.nodes.ThinkNode;
import com.openmanus.java.nodes.ActNode;
import com.openmanus.java.nodes.ObserveNode;
import com.openmanus.java.nodes.ReflectNode;
import com.openmanus.java.nodes.MemoryNode;
import java.util.Map;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * React Agent Workflow - Complete React reasoning framework based on langgraph4j StateGraph
 * 
 * This workflow implements Think->Act->Observe->Reflect cycle, supporting:
 * - Multi-round reasoning iterations
 * - Conditional edge routing
 * - Error handling and retry
 * - Memory management integration
 * - Reflection and self-optimization
 */
@Deprecated
//@Component
public class ReactAgentWorkflow {
    
    private static final Logger logger = LoggerFactory.getLogger(ReactAgentWorkflow.class);
    
    @Autowired
    private ThinkNode thinkNode;
    
    @Autowired
    private ActNode actNode;
    
    @Autowired
    private ObserveNode observeNode;
    
    @Autowired
    private ReflectNode reflectNode;
    
    @Autowired
    private MemoryNode memoryNode;
    
    /**
     * Build React Agent StateGraph workflow
     */
    public CompiledGraph<OpenManusAgentState> buildWorkflow() {
        try {
            logger.info("Start building ReactAgent StateGraph workflow...");
            
            // Create StateGraph
            StateGraph<OpenManusAgentState> stateGraph = new StateGraph<>(OpenManusAgentState.SCHEMA, OpenManusAgentState::new);
            
            return stateGraph
                // Add nodes
                .addNode("think", thinkNode)
                .addNode("act", actNode)
                .addNode("observe", observeNode)
                .addNode("reflect", reflectNode)
                .addNode("memory", memoryNode)
                
                // Set entry point
                .addEdge(StateGraph.START, "think")
                
                // Add conditional edges - Think node routing logic
                .addConditionalEdges("think", 
                    edge_async(this::routeFromThink),
                    Map.of(
                        "act", "act",
                        "finished", StateGraph.END,
                        "error", StateGraph.END
                    )
                )
                
                // Add conditional edges - Act node routing logic  
                .addConditionalEdges("act",
                    edge_async(this::routeFromAct), 
                    Map.of(
                        "observe", "observe",
                        "error", StateGraph.END,
                        "finished", StateGraph.END
                    )
                )
                
                // Add conditional edges - Observe node routing logic
                .addConditionalEdges("observe",
                    edge_async(this::routeFromObserve),
                    Map.of(
                        "reflect", "reflect", 
                        "think", "think",
                        "finished", StateGraph.END,
                        "error", StateGraph.END
                    )
                )
                
                // Add conditional edges - Reflect node routing logic
                .addConditionalEdges("reflect",
                    edge_async(this::routeFromReflect),
                    Map.of(
                        "memory", "memory",
                        "think", "think", 
                        "finished", StateGraph.END,
                        "error", StateGraph.END
                    )
                )
                
                // Memory node always returns to Think to restart
                .addEdge("memory", "think")
                
                // Compile workflow
                .compile();
                
        } catch (Exception e) {
            logger.error("Failed to build ReactAgent workflow", e);
            throw new RuntimeException("Failed to build ReactAgent workflow", e);
        }
    }
    
    /**
     * Think node routing logic
     */
    private String routeFromThink(OpenManusAgentState state) throws Exception {
        try {
            // Check for errors
            if (state.hasError()) {
                logger.warn("Think node detected error, routing to error");
                return "error";
            }
            
            // Check if max iterations reached
            if (state.isMaxIterationsReached()) {
                logger.info("Max iterations reached, routing to finished");
                return "finished";
            }
            
            // Check if final answer exists
            if (!state.getFinalAnswer().isEmpty()) {
                logger.info("Final answer exists, routing to finished");
                return "finished";
            }
            
            // Check if tool call is needed
            if (!state.getToolCalls().isEmpty()) {
                logger.info("Tool call needed, routing to act");
                return "act";
            }
            
            // Default continue thinking
            logger.info("Continue thinking, routing to think");
            return "think";
            
        } catch (Exception e) {
            logger.error("Think node routing logic exception", e);
            return "error";
        }
    }
    
    /**
     * Act node routing logic
     */
    private String routeFromAct(OpenManusAgentState state) throws Exception {
        try {
            // Check for errors
            if (state.hasError()) {
                logger.warn("Act node detected error, routing to error");
                return "error";
            }
            
            // Check if tool call is completed
            if (!state.getObservations().isEmpty()) {
                logger.info("Tool call completed, routing to observe");
                return "observe";
            }
            
            // Default continue execution
            logger.info("Continue execution, routing to act");
            return "act";
            
        } catch (Exception e) {
            logger.error("Act node routing logic exception", e);
            return "error";
        }
    }
    
    /**
     * Observe node routing logic
     */
    private String routeFromObserve(OpenManusAgentState state) throws Exception {
        try {
            // Check for errors
            if (state.hasError()) {
                logger.warn("Observe node detected error, routing to error");
                return "error";
            }
            
            // Check if reflection is needed
            if (state.getIterationCount() > 3) {
                logger.info("Many iterations, reflection needed, routing to reflect");
                return "reflect";
            }
            
            // Check if final answer exists
            if (!state.getFinalAnswer().isEmpty()) {
                logger.info("Final answer exists, routing to finished");
                return "finished";
            }
            
            // Default continue thinking
            logger.info("Continue thinking, routing to think");
            return "think";
            
        } catch (Exception e) {
            logger.error("Observe node routing logic exception", e);
            return "error";
        }
    }
    
    /**
     * Reflect node routing logic
     */
    private String routeFromReflect(OpenManusAgentState state) throws Exception {
        try {
            // Check for errors
            if (state.hasError()) {
                logger.warn("Reflect node detected error, routing to error");
                return "error";
            }
            
            // Check if memory update is needed
            if (!state.getReflections().isEmpty()) {
                logger.info("Memory update needed, routing to memory");
                return "memory";
            }
            
            // Check if final answer exists
            if (!state.getFinalAnswer().isEmpty()) {
                logger.info("Final answer exists, routing to finished");
                return "finished";
            }
            
            // Default continue thinking
            logger.info("Continue thinking, routing to think");
            return "think";
            
        } catch (Exception e) {
            logger.error("Reflect node routing logic exception", e);
            return "error";
        }
    }
    
    /**
     * Check if state has errors
     */
    private boolean hasError(OpenManusAgentState state) {
        // Simple error checking logic
        String currentStep = state.getCurrentStep();
        return currentStep != null && currentStep.contains("error");
    }
    
    /**
     * Extract observation contents
     */
    private String extractObservationContents(List<String> observations) {
        if (observations == null || observations.isEmpty()) {
            return "";
        }
        
        StringBuilder content = new StringBuilder();
        for (String obs : observations) {
            content.append(obs).append("\n");
        }
        return content.toString().trim();
    }
    
    /**
     * Generate workflow status summary
     */
    public String generateWorkflowSummary(OpenManusAgentState state) {
        StringBuilder summary = new StringBuilder();
        summary.append("=== ReactAgent Workflow Status Summary ===\n");
        summary.append(state.getReactSummary());
        summary.append("\n=== Workflow Statistics ===\n");
        summary.append("Total messages: ").append(state.getMessages().size()).append("\n");
        summary.append("Tool calls: ").append(state.getToolCalls().size()).append("\n");
        summary.append("Observations: ").append(extractObservationContents(state.getObservations())).append("\n");
        summary.append("Reflection records: ").append(state.getReflections().size()).append("\n");
        
        return summary.toString();
    }
} 