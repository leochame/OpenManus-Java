package com.openmanus.java.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A service layer that wraps the {@link ManusAgentService} AiService.
 * This class simplifies the interaction with the agent for the rest of the application,
 * providing a clean, asynchronous interface. It delegates all core AI and tool-use
 * logic to the LangChain4j AiServices framework.
 */
@Service
public class ManusAgent {

    private static final Logger logger = LoggerFactory.getLogger(ManusAgent.class);

    private final ManusAgentService agentService;

    public ManusAgent(ManusAgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * Asynchronously invokes the agent with a given conversation ID and input message.
     * This method is the primary entry point for interacting with the agent.
     *
     * @param conversationId A unique identifier for the conversation, used for memory.
     * @param input          The user's message.
     * @return A {@link CompletableFuture} containing the agent's string response.
     */
    public CompletableFuture<String> invoke(String conversationId, String input) {
        logger.info("Invoking ManusAgent for conversationId '{}'", conversationId);
        if (input == null || input.isBlank()) {
            logger.warn("Received blank input for conversationId '{}'", conversationId);
            return CompletableFuture.completedFuture("Please provide a valid message.");
        }
        return CompletableFuture.supplyAsync(() -> agentService.chat(conversationId, input));
    }

    /**
     * Provides basic information about the agent's configuration.
     *
     * @return A map containing agent information.
     */
    public Map<String, Object> getAgentInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("agent_type", "AiServices");
        info.put("message", "This agent is using a declarative AiService interface for ReAct style reasoning.");
        info.put("capabilities", Collections.singletonList("Multi-turn conversation with tools"));
        return info;
    }
}