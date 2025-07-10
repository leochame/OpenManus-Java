package com.openmanus.java.config;

import com.openmanus.java.memory.ConversationBuffer;
import com.openmanus.java.memory.MemoryTool;
import com.openmanus.java.nodes.ActNode;
import com.openmanus.java.nodes.MemoryNode;
import com.openmanus.java.nodes.ObserveNode;
import com.openmanus.java.nodes.ReflectNode;
import com.openmanus.java.nodes.ThinkNode;
import com.openmanus.java.state.OpenManusAgentState;
import com.openmanus.java.tool.BrowserTool;
import com.openmanus.java.tool.FileTool;
import com.openmanus.java.tool.PythonTool;

import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.studio.springboot.AbstractLangGraphStudioConfig;
import org.bsc.langgraph4j.studio.springboot.LangGraphFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * LangGraph4j Studio Configuration Class
 * 
 * Provides visual debugging interface for OpenManus project, supporting:
 * - StateGraph workflow visualization
 * - Real-time execution state monitoring
 * - State transfer between nodes inspection
 * - Node debugging functionality
 * - Graphical workflow editor
 */
@Configuration
public class LangGraphStudioConfig extends AbstractLangGraphStudioConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(LangGraphStudioConfig.class);
    
    private final LangGraphFlow flow;
    
    public LangGraphStudioConfig(@Qualifier("chatModel") ChatModel chatModel,
                                PythonTool pythonTool,
                                FileTool fileTool,
                                BrowserTool browserTool,
                                MemoryTool memoryTool) throws GraphStateException {
        
        logger.info("Initializing LangGraph4j Studio configuration...");
        
        // Create StateGraph workflow
        StateGraph<OpenManusAgentState> workflow = createOpenManusWorkflow(
            chatModel, pythonTool, fileTool, browserTool, memoryTool
        );
        
        // Generate Mermaid diagram for visualization
        var mermaidGraph = workflow.getGraph(GraphRepresentation.Type.MERMAID, "OpenManus ReAct Agent", false);
        logger.info("Generated Mermaid diagram:\n{}", mermaidGraph.content());
        
        // Create Studio flow configuration
        this.flow = createStudioFlow(workflow);
        
        logger.info("LangGraph4j Studio configuration initialized - Access URL: http://localhost:8089/");
    }
    
    /**
     * Create OpenManus StateGraph workflow
     */
    private StateGraph<OpenManusAgentState> createOpenManusWorkflow(
            ChatModel chatModel, 
            PythonTool pythonTool,
            FileTool fileTool, 
            BrowserTool browserTool,
            MemoryTool memoryTool) throws GraphStateException {
        
        // Create node instances
        ThinkNode thinkNode = new ThinkNode(chatModel);
        ActNode actNode = new ActNode(chatModel, pythonTool, fileTool, browserTool);
        ObserveNode observeNode = new ObserveNode(chatModel);
        // ReflectNode uses @Autowired to inject ChatModel, needs manual setup
        ReflectNode reflectNode = new ReflectNode();
        // Set chatModel field through reflection
        try {
            java.lang.reflect.Field chatModelField = ReflectNode.class.getDeclaredField("chatModel");
            chatModelField.setAccessible(true);
            chatModelField.set(reflectNode, chatModel);
        } catch (Exception e) {
             logger.error("Failed to set ChatModel for ReflectNode", e);
             throw new GraphStateException("Failed to inject ChatModel into ReflectNode: " + e.getMessage());
         }
        ConversationBuffer conversationBuffer = new ConversationBuffer();
        MemoryNode memoryNode = new MemoryNode(chatModel, memoryTool, conversationBuffer);
        
        // Define conditional edge logic
        var routeNext = edge_async((OpenManusAgentState state) -> {
            logger.debug("Routing decision - Current state: {}, iteration: {}", 
                        state.getCurrentState(), state.getIterationCount());
            
            // Check if final answer exists
            if (!state.getFinalAnswer().isEmpty()) {
                logger.info("Final answer found, ending workflow");
                return END;
            }
            
            // Check error state
            if (state.hasError()) {
                logger.warn("Error state detected: {}", state.getError());
                return END;
            }
            
            // Check max iterations
            if (state.isMaxIterationsReached()) {
                logger.warn("Reached maximum iterations: {}", state.getMaxIterations());
                return END;
            }
            
            // Determine next step based on current state and iteration count
            String currentState = state.getCurrentState();
            int iteration = state.getIterationCount();
            
            switch (currentState) {
                case "start":
                    return "memory";  // First perform memory management
                case "memory_updated":
                    return "think";   // Start thinking after memory update
                case "thinking":
                    return "act";     // Execute action after thinking
                case "acting": 
                    return "observe"; // Observe results after action
                case "observing":
                    // Perform reflection every 3 iterations
                    if (iteration > 0 && iteration % 3 == 0) {
                        return "reflect";
                    }
                    return "think";   // Continue next round of thinking
                case "reflecting":
                    return "think";   // Continue thinking after reflection
                default:
                    return "think";   // Default to thinking state
            }
        });
        
        // Build StateGraph
        try {
            return new StateGraph<OpenManusAgentState>(
                OpenManusAgentState.SCHEMA,
                initData -> new OpenManusAgentState(initData)
            )
                // Add all nodes
                .addNode("memory", memoryNode)
                .addNode("think", thinkNode)
                .addNode("act", actNode)
                .addNode("observe", observeNode)
                .addNode("reflect", reflectNode)
                
                // Define workflow
                .addEdge(START, "memory")  // Start with memory management
                
                // Add conditional edges - all nodes use the same routing logic
                .addConditionalEdges("memory", routeNext, Map.of(
                    "memory", "memory",
                    "think", "think",
                    "act", "act",
                    "observe", "observe",
                    "reflect", "reflect",
                    END, END
                ))
                .addConditionalEdges("think", routeNext, Map.of(
                    "memory", "memory",
                    "think", "think",
                    "act", "act",
                    "observe", "observe",
                    "reflect", "reflect",
                    END, END
                ))
                .addConditionalEdges("act", routeNext, Map.of(
                    "memory", "memory",
                    "think", "think",
                    "act", "act",
                    "observe", "observe",
                    "reflect", "reflect",
                    END, END
                ))
                .addConditionalEdges("observe", routeNext, Map.of(
                    "memory", "memory",
                    "think", "think",
                    "act", "act",
                    "observe", "observe",
                    "reflect", "reflect",
                    END, END
                ))
                .addConditionalEdges("reflect", routeNext, Map.of(
                    "memory", "memory",
                    "think", "think",
                    "act", "act",
                    "observe", "observe",
                    "reflect", "reflect",
                    END, END
                ));
                
        } catch (Exception e) {
            logger.error("Failed to create StateGraph", e);
            throw new GraphStateException("Failed to create StateGraph: " + e.getMessage());
        }
    }
    
    /**
     * Create Studio flow configuration
     */
    private LangGraphFlow createStudioFlow(StateGraph<OpenManusAgentState> workflow) throws GraphStateException {
        return LangGraphFlow.builder()
                .title("OpenManus ReAct Agent - Visual Debugging")
                .addInputStringArg("user_input", true, input -> input)  // User input parameter
                .addInputStringArg("session_id", false, sessionId -> sessionId != null ? sessionId : "default_session")  // Session ID
                .addInputStringArg("max_iterations", false, maxIter -> {
                    if (maxIter != null) {
                        try {
                            return Integer.parseInt(maxIter.toString());
                        } catch (NumberFormatException e) {
                            return 10;
                        }
                    }
                    return 10;
                })  // Maximum iterations
                .stateGraph(workflow)
                .compileConfig(CompileConfig.builder()
                        .checkpointSaver(new MemorySaver())  // Enable checkpoint saving
                        .releaseThread(true)                  // Enable thread release
                        .build())
                .build();
    }
    
    @Override
    public LangGraphFlow getFlow() {
        return this.flow;
    }
}