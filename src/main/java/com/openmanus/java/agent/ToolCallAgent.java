package com.openmanus.java.agent;

import com.openmanus.java.model.*;
import com.openmanus.java.tool.ToolRegistry;
import com.openmanus.java.llm.LlmClient;
import com.openmanus.java.exception.TokenLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Base agent class for handling tool/function calls with enhanced abstraction.
 *
 * This corresponds to app/agent/toolcall.py's ToolCallAgent.
 */
public class ToolCallAgent extends ReActAgent {
    private static final Logger logger = LoggerFactory.getLogger(ToolCallAgent.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TOOL_CALL_REQUIRED = "Tool call is required but no tools were called";

    // Tool management
    protected final ToolRegistry availableTools;
    protected final ToolChoice toolChoice;
    protected final Set<String> specialToolNames;
    protected List<ToolCall> toolCalls;
    protected String currentBase64Image;
    protected int maxObserve = 10000;

    public ToolCallAgent(String name, String description, String systemPrompt,
            LlmClient llm, Memory memory, ToolRegistry availableTools,
            ToolChoice toolChoice, Set<String> specialToolNames) {
        super(name, description, systemPrompt, llm, memory);
        this.availableTools = availableTools;
        this.toolChoice = toolChoice;
        this.specialToolNames = specialToolNames != null ? specialToolNames : new HashSet<>();
        this.toolCalls = new ArrayList<>();
        this.currentBase64Image = null;
    }

    public ToolCallAgent(String name, int maxSteps) {
        super(name, maxSteps);
        this.availableTools = new ToolRegistry();
        this.toolChoice = ToolChoice.AUTO;
        this.specialToolNames = new HashSet<>();
        this.toolCalls = new ArrayList<>();
        this.currentBase64Image = null;
    }

    /**
     * Process current state and decide next actions using tools.
     * Corresponds to the think() method in Python ToolCallAgent.
     */
    @Override
    public CompletableFuture<Boolean> think() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if we need to reset context due to high token usage
                if (shouldResetContext()) {
                    resetContext();
                }
                
                // Add next step prompt if available
                if (nextStepPrompt != null && !nextStepPrompt.isEmpty()) {
                    Message userMsg = Message.userMessage(nextStepPrompt);
                    memory.addMessage(userMsg);
                }

                // Prepare system messages
                List<Message> systemMessages = new ArrayList<>();
                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    systemMessages.add(Message.systemMessage(systemPrompt));
                }

                // Get response with tool options using LLM client
                Message response = llm.askTool(
                        memory.getMessages(),
                        systemMessages,
                        availableTools.getToolParams(),
                        toolChoice);

                if (response == null) {
                    throw new RuntimeException("No response received from the LLM");
                }

                // Extract tool calls and content
                this.toolCalls = response.getToolCalls() != null ? response.getToolCalls() : new ArrayList<>();
                String content = response.getContent() != null ? response.getContent() : "";

                // Log response info
                logger.info("✨ {}'s thoughts: {}", name, content);
                logger.info("🛠️ {} selected {} tools to use", name, toolCalls.size());
                if (!toolCalls.isEmpty()) {
                    logger.info("🧰 Tools being prepared: {}",
                            toolCalls.stream().map(tc -> tc.function().name()).toList());
                    logger.info("🔧 Tool arguments: {}", toolCalls.get(0).function().arguments());
                }

                // Handle different tool choice modes
                if (toolChoice == ToolChoice.NONE) {
                    if (!toolCalls.isEmpty()) {
                        logger.warn("🤔 Hmm, {} tried to use tools when they weren't available!", name);
                    }
                    if (!content.isEmpty()) {
                        memory.addMessage(Message.assistantMessage(content));
                        return true;
                    }
                    return false;
                }

                // Create and add assistant message
                Message assistantMsg;
                if (!toolCalls.isEmpty()) {
                    assistantMsg = Message.builder()
                            .role(Role.ASSISTANT)
                            .content(content)
                            .toolCalls(toolCalls)
                            .build();
                } else {
                    assistantMsg = Message.assistantMessage(content);
                }
                memory.addMessage(assistantMsg);

                if (toolChoice == ToolChoice.REQUIRED && toolCalls.isEmpty()) {
                    return true; // Will be handled in act()
                }

                // For 'auto' mode, continue with content if no commands but content exists
                if (toolChoice == ToolChoice.AUTO && toolCalls.isEmpty()) {
                    return !content.isEmpty();
                }

                return !toolCalls.isEmpty();

            } catch (TokenLimitExceededException e) {
                logger.error("🚨 Token limit error: {}", e.getMessage());
                // Try to reset context and continue
                resetContext();
                logger.info("🔄 Context reset due to token limit, retrying...");
                memory.addMessage(Message.assistantMessage(
                        "Context was reset due to token limits. Let me continue with a fresh start."));
                return true; // Continue execution with reset context
            } catch (Exception e) {
                logger.error("🚨 Oops! The {}'s thinking process hit a snag: {}", name, e.getMessage());
                memory.addMessage(Message.assistantMessage(
                        "Error encountered while processing: " + e.getMessage()));
                return false;
            }
        });
    }

    /**
     * Execute tool calls and handle their results.
     * Corresponds to the act() method in Python ToolCallAgent.
     */
    @Override
    public CompletableFuture<String> act() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (toolCalls.isEmpty()) {
                    if (toolChoice == ToolChoice.REQUIRED) {
                        throw new IllegalStateException(TOOL_CALL_REQUIRED);
                    }
                    // Return last message content if no tool calls
                    List<Message> messages = memory.getMessages();
                    if (!messages.isEmpty()) {
                        String lastContent = messages.get(messages.size() - 1).getContent();
                        return lastContent != null ? lastContent : "No content or commands to execute";
                    }
                    return "No content or commands to execute";
                }

                List<String> results = new ArrayList<>();
                for (ToolCall command : toolCalls) {
                    // Reset base64_image for each tool call
                    currentBase64Image = null;

                    String result = executeTool(command);

                    if (maxObserve > 0 && result.length() > maxObserve) {
                        result = result.substring(0, maxObserve);
                    }

                    logger.info("🎯 Tool '{}' completed its mission! Result: {}",
                            command.function().name(), result);

                    // Add tool response to memory
                    Message toolMsg = Message.builder()
                            .role(Role.TOOL)
                            .content(result)
                            .toolCallId(command.id())
                            .name(command.function().name())
                            .base64Image(currentBase64Image)
                            .build();
                    memory.addMessage(toolMsg);
                    results.add(result);
                }

                return String.join("\n\n", results);
            } catch (Exception e) {
                logger.error("Error in act(): {}", e.getMessage());
                return "Error occurred during action: " + e.getMessage();
            }
        });
    }

    /**
     * Execute a single tool call with robust error handling.
     * Corresponds to execute_tool() method in Python ToolCallAgent.
     */
    protected String executeTool(ToolCall command) {
        if (command == null || command.function() == null || command.function().name() == null) {
            return "Error: Invalid command format";
        }

        String name = command.function().name();
        if (!availableTools.hasToolByName(name)) {
            return "Error: Unknown tool '" + name + "'";
        }

        try {
            // Parse arguments
            String argsJson = command.function().arguments();
            Object args = argsJson != null ? objectMapper.readValue(argsJson, Object.class) : new Object();

            // Execute the tool
            logger.info("🔧 Activating tool: '{}'...", name);
            Object result = availableTools.execute(name, args);

            // Handle special tools
            handleSpecialTool(name, result);

            // Check if result has base64_image (for future extension)
            // if (result instanceof ToolResult && ((ToolResult) result).getBase64Image() !=
            // null) {
            // currentBase64Image = ((ToolResult) result).getBase64Image();
            // }

            // Format result for display
            String observation = result != null
                    ? String.format("Observed output of cmd `%s` executed:\n%s", name, result.toString())
                    : String.format("Cmd `%s` completed with no output", name);

            return observation;
        } catch (Exception e) {
            String errorMsg = String.format("⚠️ Tool '%s' encountered a problem: %s", name, e.getMessage());
            logger.error(errorMsg, e);
            return "Error: " + errorMsg;
        }
    }

    /**
     * Handle special tool execution and state changes.
     * Corresponds to _handle_special_tool() method in Python ToolCallAgent.
     */
    protected void handleSpecialTool(String name, Object result) {
        if (!isSpecialTool(name)) {
            return;
        }

        if (shouldFinishExecution(name, result)) {
            logger.info("🏁 Special tool '{}' has completed the task!", name);
            setState(AgentState.FINISHED);
        }
    }

    /**
     * Determine if tool execution should finish the agent.
     * Corresponds to _should_finish_execution() method in Python ToolCallAgent.
     */
    protected static boolean shouldFinishExecution(String name, Object result) {
        return true;
    }

    /**
     * Check if tool name is in special tools list.
     * Corresponds to _is_special_tool() method in Python ToolCallAgent.
     */
    protected boolean isSpecialTool(String name) {
        return specialToolNames.stream().anyMatch(n -> n.toLowerCase().equals(name.toLowerCase()));
    }

    /**
     * Clean up resources used by the agent's tools.
     * Corresponds to cleanup() method in Python ToolCallAgent.
     */
    @Override
    public void cleanup() {
        super.cleanup();
        logger.info("🧹 Cleaning up resources for agent '{}'...", name);

        availableTools.getAllTools().forEach(toolName -> {
            try {
                Object toolInstance = availableTools.getToolExecutor((String) toolName);
                if (toolInstance instanceof AutoCloseable) {
                    logger.debug("🧼 Cleaning up tool: {}", toolName);
                    ((AutoCloseable) toolInstance).close();
                }
            } catch (Exception e) {
                logger.error("🚨 Error cleaning up tool '{}': {}", toolName, e.getMessage(), e);
            }
        });

        logger.info("🧹 Agent '{}' cleanup complete.", name);
    }

    /**
     * Get the tool registry for this agent.
     * 
     * @return the tool registry
     */
    public ToolRegistry getToolRegistry() {
        return availableTools;
    }
}
