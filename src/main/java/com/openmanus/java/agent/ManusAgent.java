package com.openmanus.java.agent;

import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Intelligent Agent implementation based on LangGraph4j's built-in AgentExecutor.
 * This provides a robust implementation of the ReAct reasoning cycle.
 */
@Component
public class ManusAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(ManusAgent.class);
    
    private final CompiledGraph<AgentExecutor.State> compiledGraph;
    
    @Autowired
    public ManusAgent(StateGraph<AgentExecutor.State> agentStateGraph) throws GraphStateException {
        this.compiledGraph = agentStateGraph.compile();
        logger.info("ManusAgent initialized by compiling a pre-defined AgentExecutor state graph.");
    }
    
    /**
     * Chat with Agent (using the compiled AgentExecutor graph)
     */
    public Map<String, Object> chatWithCot(String userMessage) {
        logger.info("Starting agent execution for user message: {}", userMessage);
        
        if (userMessage == null || userMessage.isBlank()) {
            return Collections.singletonMap("answer", "Please provide a valid question.");
        }

        // The initial state only requires the user's message
        Map<String, Object> initialData = Map.of("messages", UserMessage.from(userMessage));
        
        Optional<AgentExecutor.State> finalStateOpt = Optional.empty();
        try {
            // Invoke the graph and get the final state
            logger.info("Invoking AgentExecutor workflow...");
            finalStateOpt = compiledGraph.invoke(initialData);

        } catch (Exception e) {
            logger.error("Agent execution failed", e);
            Map<String, Object> result = new HashMap<>();
            result.put("error", "An error occurred during agent execution: " + e.getMessage());
            result.put("answer", "Sorry, I encountered an error and could not complete your request.");
            return result;
        }

        // Build the final result from the agent's state
        Map<String, Object> result = new HashMap<>();
        if (finalStateOpt.isPresent()) {
            AgentExecutor.State finalState = finalStateOpt.get();
            String finalAnswer = finalState.finalResponse().orElse("Reasoning process completed, but unable to provide a clear answer.");
            result.put("answer", finalAnswer);
            // Optionally add other final state details to the result
            result.put("intermediate_steps", finalState.messages());
        } else {
            result.put("answer", "Agent execution did not produce a final state.");
            result.put("error", "Agent execution resulted in a null final state.");
        }

        logger.info("Agent execution finished. Final answer: {}", result.get("answer"));
        return result;
    }

    public Map<String, Object> getAgentInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("agent_type", "AgentExecutor");
        info.put("message", "This agent is using a pre-built graph for ReAct style reasoning with tool calling.");
        return info;
    }
}