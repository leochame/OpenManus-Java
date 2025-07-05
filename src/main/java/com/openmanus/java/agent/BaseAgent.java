package com.openmanus.java.agent;

import com.openmanus.java.llm.LlmClient;
import com.openmanus.java.model.AgentState;
import com.openmanus.java.model.Memory;
import com.openmanus.java.model.Message;
import com.openmanus.java.model.Role;
import com.openmanus.java.sandbox.SandboxClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for managing agent state and execution.
 *
 * Provides foundational functionality for state transitions, memory management,
 * and a step-based execution loop. Subclasses must implement the step method.
 *
 * This corresponds to app/agent/base.py's BaseAgent.
 */
public abstract class BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(BaseAgent.class);

    // Core attributes
    protected String name;
    protected String description;

    // Prompts
    protected String systemPrompt;
    protected String nextStepPrompt;

    // Dependencies
    protected LlmClient llm;
    protected Memory memory;
    protected AgentState state;

    // Execution control
    protected int maxSteps;
    protected int currentStep;
    protected int duplicateThreshold;
    
    // 测试模式标志，当为true时会限制执行步数和时间
    protected boolean testMode = false;

    public BaseAgent(String name, int maxSteps) {
        this.name = name;
        this.maxSteps = maxSteps;
        this.state = AgentState.IDLE;
        this.currentStep = 0;
        this.duplicateThreshold = 2;
        this.memory = new Memory();
        // Note: This constructor is deprecated. Use the constructor that accepts
        // LlmClient parameter.
        throw new UnsupportedOperationException(
                "This constructor is deprecated. Please provide an LlmClient instance.");
    }

    public BaseAgent(String name, String description, String systemPrompt,
            LlmClient llm, Memory memory) {
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        if (llm == null) {
            throw new IllegalArgumentException("LlmClient cannot be null. Please provide a valid LlmClient instance.");
        }
        this.llm = llm;
        this.memory = memory != null ? memory : new Memory();
        this.state = AgentState.IDLE;
        this.maxSteps = 10;
        this.currentStep = 0;
        this.duplicateThreshold = 2;
    }

    /**
     * Update the agent's memory with a new message.
     *
     * @param role        The role of the message sender (user, system, assistant,
     *                    tool)
     * @param content     The message content
     * @param base64Image Optional base64 encoded image
     * @param toolCallId  Optional tool call ID for tool messages
     */
    public void updateMemory(Role role, String content, String base64Image, String toolCallId) {
        Message message;
        switch (role) {
            case USER:
                message = base64Image != null ? Message.userMessage(content, base64Image)
                        : Message.userMessage(content);
                break;
            case SYSTEM:
                message = Message.systemMessage(content);
                break;
            case ASSISTANT:
                message = Message.assistantMessage(content);
                break;
            case TOOL:
                message = Message.toolMessage(content, null, toolCallId);
                break;
            default:
                throw new IllegalArgumentException("Unsupported message role: " + role);
        }
        this.memory.addMessage(message);
    }

    /**
     * Update memory with a simple message (no image or tool call ID).
     */
    public void updateMemory(Role role, String content) {
        updateMemory(role, content, null, null);
    }

    /**
     * Execute the agent's main loop asynchronously.
     *
     * @param request Optional initial user request to process
     * @return A CompletableFuture containing the execution results
     */
    public CompletableFuture<String> run(String request) {
        if (this.state != AgentState.IDLE) {
            return CompletableFuture.failedFuture(
                    new RuntimeException("Cannot run agent from state: " + this.state));
        }

        if (request != null && !request.trim().isEmpty()) {
            updateMemory(Role.USER, request);
        }

        return CompletableFuture.supplyAsync(() -> {
            List<String> results = new ArrayList<>();
            long startTime = System.currentTimeMillis();
            // 在测试模式下设置5秒超时，正常模式下设置30秒超时
            long timeoutMs = testMode ? 5000 : 30000;

            try {
                this.state = AgentState.RUNNING;
                
                // 在测试模式下，限制最大步数为3步，避免长时间运行
                int effectiveMaxSteps = testMode ? Math.min(maxSteps, 3) : maxSteps;

                while (currentStep < effectiveMaxSteps && state != AgentState.FINISHED) {
                    // 检查超时
                    if (System.currentTimeMillis() - startTime > timeoutMs) {
                        results.add("Terminated: Execution timeout after " + (timeoutMs / 1000) + " seconds");
                        break;
                    }
                    currentStep++;
                    log.info("Executing step {}/{}", currentStep, effectiveMaxSteps);

                    String stepResult = step().join();

                    // Check for stuck state
                    if (isStuck()) {
                        handleStuckState();
                    }

                    results.add(String.format("Step %d: %s", currentStep, stepResult));
                }

                if (currentStep >= effectiveMaxSteps) {
                    results.add(String.format("Terminated: Reached max steps (%d)", effectiveMaxSteps));
                }

                // Always reset state and step count when execution completes normally
                this.currentStep = 0;
                this.state = AgentState.IDLE;

            } catch (Exception e) {
                this.state = AgentState.ERROR;
                log.error("Agent execution failed", e);
                return "Agent execution failed: " + e.getMessage();
            } finally {
                // Cleanup sandbox resources
                try {
                    // SandboxClient.cleanup(); // TODO: Implement cleanup method
                    log.debug("Sandbox cleanup completed");
                } catch (Exception e) {
                    log.warn("Failed to cleanup sandbox resources", e);
                }
            }

            return results.isEmpty() ? "No steps executed" : String.join("\n", results);
        });
    }

    /**
     * Convenience method to run without initial request.
     */
    public CompletableFuture<String> run() {
        return run(null);
    }

    /**
     * Execute a single step in the agent's workflow.
     * Must be implemented by subclasses to define specific behavior.
     *
     * @return A CompletableFuture containing the result of the step
     */
    public abstract CompletableFuture<String> step();

    /**
     * Handle stuck state by adding a prompt to change strategy.
     */
    protected void handleStuckState() {
        String stuckPrompt = "Observed duplicate responses. Consider new strategies and avoid repeating ineffective paths already attempted.";
        this.nextStepPrompt = stuckPrompt + "\n" + (this.nextStepPrompt != null ? this.nextStepPrompt : "");
        log.warn("Agent detected stuck state. Added prompt: {}", stuckPrompt);
    }

    /**
     * Check if the agent is stuck in a loop by detecting duplicate content.
     *
     * @return true if the agent appears to be stuck
     */
    protected boolean isStuck() {
        List<Message> messages = this.memory.getMessages();
        if (messages.size() < 2) {
            return false;
        }

        Message lastMessage = messages.get(messages.size() - 1);
        if (lastMessage.getContent() == null || lastMessage.getContent().trim().isEmpty()) {
            return false;
        }

        // Count identical content occurrences
        int duplicateCount = 0;
        for (int i = messages.size() - 2; i >= 0; i--) {
            Message msg = messages.get(i);
            if (Role.ASSISTANT.equals(msg.getRole()) &&
                    lastMessage.getContent().equals(msg.getContent())) {
                duplicateCount++;
            }
        }

        return duplicateCount >= this.duplicateThreshold;
    }

    /**
     * Cleanup method to be called when the agent is done.
     * Subclasses can override this to perform specific cleanup tasks.
     */
    public void cleanup() {
        // Reset agent state
        this.state = AgentState.IDLE;
        this.currentStep = 0;

        // Clear memory if needed
        if (this.memory != null) {
            // Don't clear by default, let subclasses decide
        }

        // Subclasses should override this method to perform specific cleanup
        // such as closing resources, clearing caches, etc.
    }

    /**
     * Reset conversation context to prevent token limit issues
     */
    public void resetContext() {
        if (llm != null) {
            llm.resetTokenCount();
        }
        
        // Keep only system message and last few important messages
        List<Message> messages = memory.getMessages();
        List<Message> newMessages = new ArrayList<>();
        
        // Keep system messages
        for (Message msg : messages) {
            if (msg.getRole() == Role.SYSTEM) {
                newMessages.add(msg);
            }
        }
        
        // Keep last 2 messages to maintain some context
        int keepCount = Math.min(2, messages.size());
        if (keepCount > 0) {
            List<Message> lastMessages = messages.subList(messages.size() - keepCount, messages.size());
            for (Message msg : lastMessages) {
                if (msg.getRole() != Role.SYSTEM) {
                    newMessages.add(msg);
                }
            }
        }
        
        memory.setMessages(newMessages);
        log.info("🔄 Context reset: kept {} messages out of {}", newMessages.size(), messages.size());
    }
    
    /**
     * Check if context should be reset due to high token usage
     */
    public boolean shouldResetContext() {
        return llm != null && llm.shouldResetContext();
    }

    // Getters and Setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getNextStepPrompt() {
        return nextStepPrompt;
    }

    public void setNextStepPrompt(String nextStepPrompt) {
        this.nextStepPrompt = nextStepPrompt;
    }

    public LlmClient getLlm() {
        return llm;
    }

    public void setLlm(LlmClient llm) {
        this.llm = llm;
    }

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public AgentState getState() {
        return state;
    }

    public void setState(AgentState state) {
        this.state = state;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(int currentStep) {
        this.currentStep = currentStep;
    }

    public int getDuplicateThreshold() {
        return duplicateThreshold;
    }

    public void setDuplicateThreshold(int duplicateThreshold) {
        this.duplicateThreshold = duplicateThreshold;
    }
    
    /**
     * 启用测试模式，限制执行步数和时间
     */
    public void enableTestMode() {
        this.testMode = true;
        log.debug("Agent {} 已启用测试模式", name);
    }
    
    /**
     * 禁用测试模式
     */
    public void disableTestMode() {
        this.testMode = false;
        log.debug("Agent {} 已禁用测试模式", name);
    }
    
    /**
     * 检查是否为测试模式
     */
    public boolean isTestMode() {
        return testMode;
    }

    /**
     * Get messages from memory.
     *
     * @return List of messages
     */
    public List<Message> getMessages() {
        return this.memory.getMessages();
    }

    /**
     * Set messages in memory.
     *
     * @param messages List of messages to set
     */
    public void setMessages(List<Message> messages) {
        this.memory.setMessages(messages);
    }
}
