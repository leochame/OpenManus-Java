package com.openmanus.java.agent;

import com.openmanus.java.llm.LlmClient;
import com.openmanus.java.model.Memory;
import com.openmanus.java.model.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * ReAct (Reasoning and Acting) agent that implements the think-act cycle.
 *
 * This corresponds to app/agent/react.py's ReActAgent.
 */
public abstract class ReActAgent extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(ReActAgent.class);

    public ReActAgent(String name, int maxSteps) {
        super(name, maxSteps);
    }

    public ReActAgent(String name, String description, String systemPrompt,
            LlmClient llm, Memory memory) {
        super(name, description, systemPrompt, llm, memory);
    }

    /**
     * Process current state and decide next action.
     * Must be implemented by subclasses to define specific thinking logic.
     *
     * @return A CompletableFuture<Boolean> indicating whether the agent should
     *         proceed to act
     */
    public abstract CompletableFuture<Boolean> think();

    /**
     * Execute decided actions.
     * Must be implemented by subclasses to define specific action logic.
     *
     * @return A CompletableFuture<String> containing the result of the action
     */
    public abstract CompletableFuture<String> act();

    /**
     * Execute a single step: think and act.
     * Implements the step method from BaseAgent.
     *
     * @return A CompletableFuture<String> containing the result of the step
     */
    @Override
    public CompletableFuture<String> step() {
        return think().thenCompose(shouldAct -> {
            if (shouldAct) {
                return act();
            } else {
                return CompletableFuture.completedFuture("Thinking complete - no action needed");
            }
        }).exceptionally(throwable -> {
            log.error("Error during ReAct step execution", throwable);
            return "Error during step execution: " + throwable.getMessage();
        });
    }
}
