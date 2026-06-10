package com.openmanus.agent.base;

import com.openmanus.agent.context.ToolResultBudget;
import com.openmanus.agent.context.assembly.ContextAssembler;
import com.openmanus.agent.context.assembly.ContextSnapshot;
import com.openmanus.agent.context.assembly.TaskExecutionState;
import com.openmanus.agent.context.assembly.TaskExecutionStateTracker;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiMemory;
import com.openmanus.aiframework.runtime.AiMemoryProvider;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.AiSystemMessageMemory;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatRequest;
import com.openmanus.aiframework.runtime.model.AiChatResponse;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import com.openmanus.aiframework.runtime.model.AiToolSpec;
import com.openmanus.aiframework.tool.AiRegisteredTool;
import com.openmanus.aiframework.tool.AiToolExecutionRequest;
import com.openmanus.aiframework.tool.AiToolRegistry;
import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.service.ExecutionEventPort;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * An abstract base class for creating agent executors that operate in a ReAct (Reason-Act) loop.
 * This class manages the interaction between a language model, a set of tools, and the user's request.
 * It orchestrates the cycle of generating a response (thought), executing a tool if requested,
 * and feeding the result back into the model until a final answer is produced.
 *
 * @param <B> The type of the builder used to construct this executor, allowing for fluent chaining in subclasses.
 */
@Slf4j
public abstract class AbstractAgentExecutor<B extends AbstractAgentExecutor.Builder<B>> extends AbstractAgent<B> {

    /**
     * An abstract builder for configuring and creating instances of {@link AbstractAgentExecutor}.
     *
     * @param <B> The concrete builder type, enabling method chaining.
     */
    public static abstract class Builder<B extends AbstractAgentExecutor.Builder<B>> extends AbstractAgent.Builder<B> {

        AiChatModel aiChatModel;
        String systemMessage;
        AiMemoryProvider aiMemoryProvider;
        int maxIterations = 0;
        int maxExecutionSeconds = 0;
        int repeatedToolCallThreshold = 0;
        int taskStatePlanMaxChars = TaskExecutionState.Budget.DEFAULT_PLAN_MAX_CHARS;
        int taskStateInProgressMaxChars = TaskExecutionState.Budget.DEFAULT_IN_PROGRESS_MAX_CHARS;
        int taskStateLastFailureMaxChars = TaskExecutionState.Budget.DEFAULT_LAST_FAILURE_MAX_CHARS;
        int taskStateTodoMaxItems = TaskExecutionState.Budget.DEFAULT_TODO_MAX_ITEMS;
        int taskStateTodoItemMaxChars = TaskExecutionState.Budget.DEFAULT_TODO_ITEM_MAX_CHARS;
        AiSessionSandboxGateway sessionSandboxGateway;
        boolean toolResultBudgetEnabled = true;
        int toolResultBudgetMinChars = 12000;
        int toolResultBudgetPreviewHeadChars = 240;
        int toolResultBudgetPreviewTailChars = 160;
        int toolResultBudgetDecayChars = 0;
        ExecutionEventPort executionEventPort;
        final Map<String, AiRegisteredTool> tools = new HashMap<>();

        /**
         * Sets the execution event port for publishing progress events to WebSocket.
         */
        public B executionEventPort(ExecutionEventPort executionEventPort) {
            this.executionEventPort = executionEventPort;
            return result();
        }

        /**
         * Sets runtime-first chat model used by the agent loop.
         */
        public B aiChatModel(AiChatModel model) {
            this.aiChatModel = model;
            return result();
        }

        /**
         * Sets runtime-first chat memory provider used by the agent loop.
         */
        public B aiMemoryProvider(AiMemoryProvider aiMemoryProvider) {
            this.aiMemoryProvider = aiMemoryProvider;
            return result();
        }

        /**
         * Limits ReAct loop iterations for one execute.
         * 0 means unlimited (continue as long as model keeps producing tool calls).
         */
        public B maxIterations(int maxIterations) {
            this.maxIterations = Math.max(0, maxIterations);
            return result();
        }

        /**
         * Limits execution time for one execute in seconds.
         * 0 means unlimited.
         */
        public B maxExecutionSeconds(int maxExecutionSeconds) {
            this.maxExecutionSeconds = Math.max(0, maxExecutionSeconds);
            return result();
        }

        /**
         * Detects repeated identical tool-call batches and aborts after threshold.
         * 0 means disabled.
         */
        public B repeatedToolCallThreshold(int repeatedToolCallThreshold) {
            this.repeatedToolCallThreshold = Math.max(0, repeatedToolCallThreshold);
            return result();
        }

        public B taskStatePlanMaxChars(int maxChars) {
            this.taskStatePlanMaxChars = maxChars <= 0
                    ? TaskExecutionState.Budget.DEFAULT_PLAN_MAX_CHARS
                    : maxChars;
            return result();
        }

        public B taskStateInProgressMaxChars(int maxChars) {
            this.taskStateInProgressMaxChars = maxChars <= 0
                    ? TaskExecutionState.Budget.DEFAULT_IN_PROGRESS_MAX_CHARS
                    : maxChars;
            return result();
        }

        public B taskStateLastFailureMaxChars(int maxChars) {
            this.taskStateLastFailureMaxChars = maxChars <= 0
                    ? TaskExecutionState.Budget.DEFAULT_LAST_FAILURE_MAX_CHARS
                    : maxChars;
            return result();
        }

        public B taskStateTodoMaxItems(int maxItems) {
            this.taskStateTodoMaxItems = maxItems <= 0
                    ? TaskExecutionState.Budget.DEFAULT_TODO_MAX_ITEMS
                    : maxItems;
            return result();
        }

        public B taskStateTodoItemMaxChars(int maxChars) {
            this.taskStateTodoItemMaxChars = maxChars <= 0
                    ? TaskExecutionState.Budget.DEFAULT_TODO_ITEM_MAX_CHARS
                    : maxChars;
            return result();
        }

        public B sessionSandboxGateway(AiSessionSandboxGateway sessionSandboxGateway) {
            this.sessionSandboxGateway = sessionSandboxGateway;
            return result();
        }

        public B enableToolResultBudget(boolean enabled) {
            this.toolResultBudgetEnabled = enabled;
            return result();
        }

        public B toolResultBudgetMinChars(int minChars) {
            this.toolResultBudgetMinChars = Math.max(256, minChars);
            return result();
        }

        public B toolResultBudgetPreviewHeadChars(int headChars) {
            this.toolResultBudgetPreviewHeadChars = Math.max(64, headChars);
            return result();
        }

        public B toolResultBudgetPreviewTailChars(int tailChars) {
            this.toolResultBudgetPreviewTailChars = Math.max(32, tailChars);
            return result();
        }

        public B toolResultBudgetDecayChars(int decayChars) {
            this.toolResultBudgetDecayChars = Math.max(0, decayChars);
            return result();
        }

        /**
         * Adds a pre-configured tool to the agent.
         * @param tool A prebuilt tool registration.
         * @return The builder instance for chaining.
         */
        public B tool(AiRegisteredTool tool) {
            registerTool(tool);
            return result();
        }

        /**
         * Scans an object for methods annotated with {@link com.openmanus.aiframework.tool.AiTool}
         * and adds them as executable tools for the agent.
         *
         * @param objectWithTools The object containing tool methods.
         * @return The builder instance for chaining.
         */
        public B toolFromObject( Object objectWithTools ) {
            AiToolRegistry.scan(objectWithTools).forEach(this::registerTool);
            return result();
        }

        /**
         * A convenience method to add tools from multiple objects.
         *
         * @param objectsWithTools A varargs array of objects containing tool methods.
         * @return The builder instance for chaining.
         */
        public B toolsFromObjects( Object... objectsWithTools ) {
            for (Object tool : objectsWithTools) {
                toolFromObject(tool);
            }
            return result();
        }

        private void registerTool(AiRegisteredTool tool) {
            Objects.requireNonNull(tool, "tool cannot be null");
            AiRegisteredTool existing = tools.putIfAbsent(tool.name(), tool);
            if (existing != null) {
                throw new IllegalStateException("Duplicate tool name detected: " + tool.name());
            }
        }

        public B systemMessage(String message) {
            this.systemMessage = Objects.requireNonNull(message, "message cannot be null");
            return result();
        }

        protected String configuredSystemMessage() {
            return systemMessage;
        }
    }

    private final AiChatModel aiChatModel;
    private final String systemMessage;
    private final AiMemoryProvider aiMemoryProvider;
    private final int maxIterations;
    private final int maxExecutionSeconds;
    private final int repeatedToolCallThreshold;
    private final TaskExecutionState.Budget taskStateBudget;
    private final ToolResultBudget toolResultBudget;
    private final ExecutionEventPort executionEventPort;
    private final ContextAssembler contextAssembler;
    private final Map<String, AiRegisteredTool> tools;
    private final List<AiToolSpec> toolSpecifications;
    private static final int SYSTEM_MESSAGE_LOCK_STRIPES = 1024;
    private static final int MAX_CONSECUTIVE_UNKNOWN_TOOL_CALLS = 3;
    private static final ReentrantLock[] SYSTEM_MESSAGE_LOCKS = createSystemMessageLocks();

    public AbstractAgentExecutor( Builder<B> builder ) {
        super( builder );
        this.aiChatModel = resolveAiChatModel(builder);
        this.systemMessage = builder.systemMessage;
        this.aiMemoryProvider = builder.aiMemoryProvider;
        this.maxIterations = builder.maxIterations;
        this.maxExecutionSeconds = builder.maxExecutionSeconds;
        this.repeatedToolCallThreshold = builder.repeatedToolCallThreshold;
        this.taskStateBudget = new TaskExecutionState.Budget(
                builder.taskStatePlanMaxChars,
                builder.taskStateInProgressMaxChars,
                builder.taskStateLastFailureMaxChars,
                builder.taskStateTodoMaxItems,
                builder.taskStateTodoItemMaxChars
        );
        this.toolResultBudget = new ToolResultBudget(
                builder.sessionSandboxGateway,
                builder.toolResultBudgetEnabled,
                builder.toolResultBudgetMinChars,
                builder.toolResultBudgetPreviewHeadChars,
                builder.toolResultBudgetPreviewTailChars,
                builder.toolResultBudgetDecayChars
        );
        this.executionEventPort = builder.executionEventPort;
        this.contextAssembler = new ContextAssembler(this.taskStateBudget);
        this.tools = Collections.unmodifiableMap(new HashMap<>(builder.tools));
        this.toolSpecifications = AiToolRegistry.toRuntimeToolSpecifications(this.tools.values());
    }

    private AiChatModel resolveAiChatModel(Builder<B> builder) {
        if (builder.aiChatModel == null) {
            throw new IllegalStateException("aiChatModel must be configured");
        }
        return builder.aiChatModel;
    }

    /**
     * Executes the agent's ReAct loop to process a user request.
     * The method initiates a conversation with the language model, executes tools as directed by the model,
     * and continues this cycle until the model provides a final answer or the maximum number of iterations is reached.
     *
     * @param userInput The incoming user prompt.
     * @param memoryId A unique identifier for the conversation memory, allowing the agent to maintain context
     *                 across multiple turns (if the underlying tools support it).
     * @return The final text response from the agent.
     * @throws RuntimeException if the agent exceeds the maximum number of iterations or if the model fails to respond.
     */
    public String execute(String userInput, Object memoryId) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("userInput cannot be null or blank");
        }
        log.debug("agent_execution_start inputLength={} memoryId={}",
                userInput.length(),
                memoryId != null ? memoryId.toString() : "null");

        AiMemory memory = resolveMemory(memoryId);

        MessageListState messageList = initializeMessageList(memory);

        boolean hasSystemMessage = messageList.messages().stream()
                .anyMatch(message -> message != null && message.role() == AiChatMessage.Role.SYSTEM);
        if (!hasSystemMessage && systemMessage != null && !systemMessage.isBlank()) {
            AiChatMessage runtimeSystemMessage = AiChatMessage.system(systemMessage);
            messageList.addAtHead(runtimeSystemMessage);
            if (memory != null) {
                ensureSystemMessageInMemory(memory, memoryId, runtimeSystemMessage);
            }
        }
        log.debug("context_prepared memoryId={} messageCount={}", memoryId, messageList.size());

        AiChatMessage userMessage = extractUserMessageFromInput(userInput);
        messageList.appendAndPersist(userMessage, memory);

        long startNs = System.nanoTime();
        TaskExecutionState taskExecutionState = TaskExecutionState.empty(taskStateBudget);
        List<AiChatMessage> pendingToolResults = new ArrayList<>();
        String lastToolBatchFingerprint = null;
        int repeatedToolBatchCount = 0;
        String lastUnknownToolFingerprint = null;
        int consecutiveUnknownToolBatchCount = 0;
        for (int i = 0; ; i++) {
            if (maxIterations > 0 && i >= maxIterations) {
                throw new RuntimeException("Agent exceeded maximum iterations (" + maxIterations + ")");
            }
            if (maxExecutionSeconds > 0) {
                long elapsedSeconds = (System.nanoTime() - startNs) / 1_000_000_000L;
                if (elapsedSeconds >= maxExecutionSeconds) {
                    throw new RuntimeException("Agent exceeded maximum execution seconds (" + maxExecutionSeconds + ")");
                }
            }
            log.debug("agent_iteration iteration={}", i + 1);

            publishIterationEvent(memoryId, i + 1, taskExecutionState);
            flushPendingToolResults(messageList, pendingToolResults, memory);

            List<AiChatMessage> modelMessagesBeforeBudget = buildModelMessages(
                    messageList.messages(),
                    userMessage,
                    taskExecutionState
            );
            logModelRequestSnapshot(
                    i + 1,
                    modelMessagesBeforeBudget
            );
            validateToolCallProtocol(i + 1, modelMessagesBeforeBudget);
            AiChatRequest runtimeRequest = new AiChatRequest(
                    "",
                    modelMessagesBeforeBudget,
                    toolSpecifications,
                    null,
                    null,
                    null,
                    null
            );
            publishKeyEvent(
                    memoryId,
                    AgentExecutionEvent.EventType.LLM_REQUEST,
                    "llm",
                    "AI_MODEL",
                    "RUNNING",
                    null,
                    Map.of(
                            "iteration", i + 1,
                            "messageCount", modelMessagesBeforeBudget.size(),
                            "toolCount", toolSpecifications.size()
                    ),
                    modelRequestSummary(modelMessagesBeforeBudget)
            );
            AiChatResponse runtimeResponse = aiChatModel.chat(runtimeRequest);
            if (runtimeResponse == null || runtimeResponse.message() == null) {
                throw new RuntimeException("LLM failed to generate a response.");
            }

            AiChatMessage assistantMessage = normalizeAssistantToolCallIds(runtimeResponse.message());
            messageList.appendAndPersist(assistantMessage, memory);
            publishKeyEvent(
                    memoryId,
                    AgentExecutionEvent.EventType.LLM_RESPONSE,
                    "llm",
                    "AI_MESSAGE",
                    "SUCCESS",
                    null,
                    llmResponseMetadata(i + 1, runtimeResponse, assistantMessage),
                    assistantMessageSummary(assistantMessage)
            );
            taskExecutionState = TaskExecutionStateTracker.updateFromAssistantPlan(
                    taskExecutionState,
                    assistantMessage,
                    taskStateBudget
            );

            if (assistantMessage.toolCalls() == null || assistantMessage.toolCalls().isEmpty()) {
                log.debug("agent_finished");
                return assistantMessage.content() == null ? "" : assistantMessage.content();
            }

            List<AiToolCall> requests = assistantMessage.toolCalls();
            List<AiToolCall> validRequests = requests.stream()
                    .filter(request -> request != null
                            && request.name() != null
                            && !request.name().isBlank())
                    .collect(Collectors.toList());
            if (validRequests.isEmpty()) {
                throw new RuntimeException("LLM returned tool calls without a valid tool name.");
            }
            String currentFingerprint = toolBatchFingerprint(validRequests);
            if (repeatedToolCallThreshold > 0) {
                if (Objects.equals(lastToolBatchFingerprint, currentFingerprint)) {
                    repeatedToolBatchCount++;
                } else {
                    repeatedToolBatchCount = 1;
                    lastToolBatchFingerprint = currentFingerprint;
                }
                if (repeatedToolBatchCount > repeatedToolCallThreshold) {
                    throw new RuntimeException("Agent aborted due to repeated identical tool-call batch (threshold="
                            + repeatedToolCallThreshold + ")");
                }
            }
            boolean unknownToolOnlyBatch = validRequests.stream()
                    .allMatch(request -> !tools.containsKey(request.name()));
            if (unknownToolOnlyBatch) {
                if (Objects.equals(lastUnknownToolFingerprint, currentFingerprint)) {
                    consecutiveUnknownToolBatchCount++;
                } else {
                    lastUnknownToolFingerprint = currentFingerprint;
                    consecutiveUnknownToolBatchCount = 1;
                }
                if (consecutiveUnknownToolBatchCount > MAX_CONSECUTIVE_UNKNOWN_TOOL_CALLS) {
                    throw new RuntimeException(
                            "Agent aborted due to repeated unknown tool-call batch (threshold="
                                    + MAX_CONSECUTIVE_UNKNOWN_TOOL_CALLS + ")");
                }
            } else {
                lastUnknownToolFingerprint = null;
                consecutiveUnknownToolBatchCount = 0;
            }

            for (AiToolCall request : validRequests) {
                String toolCallId = resolveToolCallId(request);
                AiToolExecutionRequest toolRequest =
                        new AiToolExecutionRequest(toolCallId, request.name(), request.arguments());
                taskExecutionState = TaskExecutionStateTracker.markToolStarted(
                        taskExecutionState,
                        request.name(),
                        taskStateBudget
                );
                log.debug("tool_call_start name={}", request.name());
                publishKeyEvent(
                        memoryId,
                        AgentExecutionEvent.EventType.TOOL_CALL_START,
                        request.name(),
                        "TOOL_CALL",
                        "RUNNING",
                        toolRequest.arguments(),
                        Map.of(
                                "toolName", request.name(),
                                "toolCallId", toolCallId
                        ),
                        null
                );

                AiRegisteredTool toolEntry = tools.get(request.name());
                String outcome;
                if (toolEntry == null) {
                    outcome = "Tool not found: " + request.name();
                    taskExecutionState = TaskExecutionStateTracker.markToolFailed(
                            taskExecutionState,
                            request.name(),
                            outcome,
                            taskStateBudget
                    );
                } else {
                    try {
                        outcome = toolEntry.executor().execute(toolRequest, memoryId);
                        taskExecutionState = TaskExecutionStateTracker.markToolSucceeded(
                                taskExecutionState,
                                request.name(),
                                taskStateBudget
                        );
                    } catch (RuntimeException ex) {
                        taskExecutionState = TaskExecutionStateTracker.markToolFailed(
                                taskExecutionState,
                                request.name(),
                                ex.getMessage(),
                                taskStateBudget
                        );
                        throw ex;
                    }
                }
                publishKeyEvent(
                        memoryId,
                        AgentExecutionEvent.EventType.TOOL_CALL_END,
                        request.name(),
                        "TOOL_CALL",
                        toolEntry == null ? "FAILED" : "SUCCESS",
                        toolRequest.arguments(),
                        Map.of(
                                "toolName", request.name(),
                                "toolCallId", toolCallId
                        ),
                        outcome
                );

                AiChatMessage rawToolResult = toolMessage(toolRequest.id(), toolRequest.name(), outcome);
                messageList.appendForModel(rawToolResult);
                pendingToolResults.add(rawToolResult);
                log.debug("tool_call_end name={}", request.name());
            }
        }
    }

    private void flushPendingToolResults(MessageListState messageList,
                                         List<AiChatMessage> pendingToolResults,
                                         AiMemory memory) {
        if (pendingToolResults == null || pendingToolResults.isEmpty()) {
            return;
        }
        int stubbed = 0;
        for (AiChatMessage pending : new ArrayList<>(pendingToolResults)) {
            AiChatMessage budgeted = toolResultBudget.budget(pending);
            if (budgeted != pending) {
                messageList.replaceIdentity(pending, budgeted);
                stubbed++;
            }
            persistToMemory(memory, budgeted);
        }
        log.debug("tool-result-budget pending={} stubbed={}", pendingToolResults.size(), stubbed);
        pendingToolResults.clear();
    }

    private AiChatMessage normalizeAssistantToolCallIds(AiChatMessage message) {
        if (message == null
                || message.role() != AiChatMessage.Role.ASSISTANT
                || message.toolCalls() == null
                || message.toolCalls().isEmpty()) {
            return message;
        }
        boolean changed = false;
        List<AiToolCall> normalized = new ArrayList<>(message.toolCalls().size());
        for (AiToolCall call : message.toolCalls()) {
            if (call == null) {
                normalized.add(null);
                continue;
            }
            if (call.id() == null || call.id().isBlank()) {
                normalized.add(call.withId(resolveToolCallId(call)));
                changed = true;
            } else {
                normalized.add(call);
            }
        }
        if (!changed) {
            return message;
        }
        return new AiChatMessage(
                AiChatMessage.Role.ASSISTANT,
                message.content(),
                message.name(),
                message.toolCallId(),
                normalized
        );
    }

    private AiMemory resolveMemory(Object memoryId) {
        if (aiMemoryProvider == null || memoryId == null) {
            return null;
        }
        AiMemory memory = aiMemoryProvider.get(memoryId);
        if (memory == null) {
            throw new IllegalStateException(
                    "aiMemoryProvider returned null for memoryId: " + memoryId);
        }
        return memory;
    }

    /**
     * Extracts the user's core message from runtime user input.
     * The default execution mode uses plain-text arguments.
     * For backward compatibility, only strict legacy shape {"context":"..."} is unpacked.
     */
    private AiChatMessage extractUserMessageFromInput(String userInput) {
        String arguments = userInput;
        if (arguments == null) {
            throw new IllegalArgumentException("userInput cannot be null or blank");
        }
        try {
            var jsonArgs = com.google.gson.JsonParser.parseString(arguments).getAsJsonObject();
            boolean strictLegacyShape = jsonArgs.size() == 1
                    && jsonArgs.has("context")
                    && jsonArgs.get("context").isJsonPrimitive()
                    && jsonArgs.get("context").getAsJsonPrimitive().isString();
            if (strictLegacyShape) {
                arguments = jsonArgs.get("context").getAsString();
            }
        } catch (Exception e) {
            // Plain-text arguments are the default in the current execution pipeline.
        }
        if (arguments.isBlank()) {
            throw new IllegalArgumentException("userInput cannot be null or blank");
        }
        return AiChatMessage.user(arguments);
    }

    private List<AiChatMessage> buildModelMessages(List<AiChatMessage> fullMessages,
                                                   AiChatMessage currentUserMessage,
                                                   TaskExecutionState taskExecutionState) {
        ContextSnapshot snapshot = ContextSnapshot.from(fullMessages, currentUserMessage);
        return contextAssembler.assemble(snapshot, taskExecutionState);
    }

    private static String toolBatchFingerprint(List<AiToolCall> requests) {
        return requests.stream()
                .map(request -> request.name() + "|" + String.valueOf(request.arguments()))
                .collect(Collectors.joining("||"));
    }

    private static String resolveToolCallId(AiToolCall request) {
        if (request != null && request.id() != null && !request.id().isBlank()) {
            return request.id();
        }
        return "call_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static List<AiChatMessage> tail(List<AiChatMessage> source, int size) {
        if (source == null || size <= 0 || source.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, source.size() - size);
        return new ArrayList<>(source.subList(from, source.size()));
    }

    private static MessageListState initializeMessageList(AiMemory memory) {
        if (memory == null) {
            return new MessageListState();
        }
        return new MessageListState(copyOwnedMessages(memory.messages()));
    }

    private static List<AiChatMessage> copyOwnedMessages(List<AiChatMessage> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(source);
    }

    private static void persistToMemory(AiMemory memory, AiChatMessage message) {
        if (memory == null || message == null) {
            return;
        }
        memory.add(message);
    }

    private static AiChatMessage toolMessage(String toolCallId, String toolName, String content) {
        return new AiChatMessage(AiChatMessage.Role.TOOL, content, toolName, toolCallId, List.of());
    }

    private static final class MessageListState {
        private final List<AiChatMessage> messages;

        private MessageListState() {
            this.messages = new ArrayList<>();
        }

        private MessageListState(List<AiChatMessage> initialMessages) {
            this.messages = initialMessages == null ? new ArrayList<>() : initialMessages;
        }

        private List<AiChatMessage> messages() {
            return messages;
        }

        private int size() {
            return messages.size();
        }

        private void addAtHead(AiChatMessage message) {
            if (message == null) {
                return;
            }
            messages.add(0, message);
        }

        private void appendForModel(AiChatMessage message) {
            if (message == null) {
                return;
            }
            messages.add(message);
        }

        private void appendAndPersist(AiChatMessage message, AiMemory memory) {
            appendForModel(message);
            persistToMemory(memory, message);
        }

        private void replaceIdentity(AiChatMessage expected, AiChatMessage replacement) {
            if (expected == null || replacement == null) {
                return;
            }
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i) == expected) {
                    messages.set(i, replacement);
                    return;
                }
            }
        }
    }

    private static void ensureSystemMessageInMemory(AiMemory memory, Object memoryId, AiChatMessage message) {
        if (memory instanceof AiSystemMessageMemory systemMessageMemory) {
            systemMessageMemory.ensureSystemMessage(message.content());
            return;
        }
        ReentrantLock lock = lockForSystemMessage(memoryId);
        lock.lock();
        try {
            boolean exists = memory.messages().stream()
                    .anyMatch(chatMessage -> chatMessage != null
                            && chatMessage.role() == AiChatMessage.Role.SYSTEM
                            && Objects.equals(chatMessage.content(), message.content()));
            if (!exists) {
                memory.add(AiChatMessage.system(message.content()));
            }
        } finally {
            lock.unlock();
        }
    }

    private static ReentrantLock lockForSystemMessage(Object memoryId) {
        String key = String.valueOf(memoryId);
        int index = Math.floorMod(key.hashCode(), SYSTEM_MESSAGE_LOCK_STRIPES);
        return SYSTEM_MESSAGE_LOCKS[index];
    }

    private static ReentrantLock[] createSystemMessageLocks() {
        ReentrantLock[] locks = new ReentrantLock[SYSTEM_MESSAGE_LOCK_STRIPES];
        for (int i = 0; i < SYSTEM_MESSAGE_LOCK_STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }

    private void logModelRequestSnapshot(int iteration,
                                         List<AiChatMessage> messages) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "model_request_snapshot iter={} message.count={} tool_result_blocks={} tool_result_stubs={}",
                iteration,
                sizeOf(messages),
                countToolResultBlocks(messages),
                countToolResultStubs(messages)
        );
    }

    private void validateToolCallProtocol(int iteration, List<AiChatMessage> messages) {
        List<String> violations = findToolCallProtocolViolations(messages);
        if (violations.isEmpty()) {
            return;
        }
        log.error(
                "tool_call_protocol_violation iter={} violations={} sequence={}",
                iteration,
                violations,
                modelRequestSummary(messages)
        );
        throw new IllegalStateException("Invalid tool-call message sequence before provider request: "
                + String.join("; ", violations));
    }

    private static int sizeOf(List<AiChatMessage> messages) {
        return messages == null ? 0 : messages.size();
    }

    private static int countToolResultBlocks(List<AiChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (AiChatMessage message : messages) {
            if (message != null
                    && message.role() == AiChatMessage.Role.ASSISTANT
                    && message.toolCalls() != null
                    && !message.toolCalls().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static int countToolResultStubs(List<AiChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (AiChatMessage message : messages) {
            if (message != null && message.role() == AiChatMessage.Role.TOOL) {
                String text = message.content();
                if (text != null && text.contains("[Tool Result Stub]")) {
                    count++;
                }
            }
        }
        return count;
    }

    private void publishIterationEvent(Object memoryId, int iteration, TaskExecutionState taskState) {
        if (executionEventPort == null || memoryId == null) {
            return;
        }
        String sessionId = memoryId.toString();
        String taskPlan = taskState != null ? taskState.plan() : "";
        String currentTool = taskState != null ? taskState.inProgress() : "";

        var event = new com.openmanus.domain.model.AgentExecutionEvent();
        event.setSessionId(sessionId);
        event.setEventId(java.util.UUID.randomUUID().toString());
        event.setAgentName("execution_coordinator");
        event.setAgentType("AGENT_ITERATION");
        event.setEventType(com.openmanus.domain.model.AgentExecutionEvent.EventType.AGENT_START);
        event.setStatus("RUNNING");
        event.setMetadata(java.util.Map.of(
            "iteration", iteration,
            "taskPlan", taskPlan != null ? taskPlan : "",
            "currentTool", currentTool != null ? currentTool : ""
        ));

        executionEventPort.recordCustomEvent(event);
        log.debug("Published iteration event: sessionId={}, iteration={}", sessionId, iteration);
    }

    private void publishKeyEvent(Object memoryId,
                                 AgentExecutionEvent.EventType eventType,
                                 String agentName,
                                 String agentType,
                                 String status,
                                 Object input,
                                 Map<String, Object> metadata,
                                 Object output) {
        if (executionEventPort == null || memoryId == null) {
            return;
        }
        AgentExecutionEvent event = AgentExecutionEvent.builder()
                .sessionId(memoryId.toString())
                .eventId(UUID.randomUUID().toString())
                .agentName(agentName)
                .agentType(agentType)
                .eventType(eventType)
                .status(status)
                .startTime(java.time.LocalDateTime.now())
                .metadata(metadata)
                .build();
        event.setInput(input);
        event.setOutput(output);
        executionEventPort.recordCustomEvent(event);
        logKeyEvent(memoryId, eventType, agentName, status, input, output);
    }

    private void logKeyEvent(Object memoryId,
                             AgentExecutionEvent.EventType eventType,
                             String agentName,
                             String status,
                             Object input,
                             Object output) {
        if (eventType == null || memoryId == null) {
            return;
        }
        if (eventType == AgentExecutionEvent.EventType.LLM_RESPONSE) {
            log.info("llm_response sessionId={} status={} output={}",
                    memoryId,
                    status,
                    summarize(String.valueOf(output), 240));
            return;
        }
        if (eventType == AgentExecutionEvent.EventType.TOOL_CALL_START
                || eventType == AgentExecutionEvent.EventType.TOOL_CALL_END) {
            log.info("tool_event sessionId={} tool={} event={} status={} input={} output={}",
                    memoryId,
                    agentName,
                    eventType,
                    status,
                    summarize(String.valueOf(input), 240),
                    summarize(String.valueOf(output), 240));
        }
    }

    private static Map<String, Object> llmResponseMetadata(int iteration,
                                                           AiChatResponse response,
                                                           AiChatMessage message) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("iteration", iteration);
        metadata.put("model", response.model() == null ? "" : response.model());
        metadata.put("responseId", response.responseId() == null ? "" : response.responseId());
        metadata.put("finishReason", response.finishReason() == null ? "" : response.finishReason().name());
        metadata.put("toolCallCount", message.toolCalls() == null ? 0 : message.toolCalls().size());
        if (response.tokenUsage() != null) {
            metadata.put("inputTokens", response.tokenUsage().inputTokens());
            metadata.put("outputTokens", response.tokenUsage().outputTokens());
            metadata.put("totalTokens", response.tokenUsage().totalTokens());
        }
        return metadata;
    }

    private static String modelRequestSummary(List<AiChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "messages=0";
        }
        List<String> summary = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            summary.add(summarizeRequestMessage(i, messages.get(i)));
        }
        return String.join("\n", summary);
    }

    private static String summarizeRequestMessage(int index, AiChatMessage message) {
        if (message == null) {
            return "[" + index + "] null";
        }
        StringBuilder summary = new StringBuilder("[")
                .append(index)
                .append("] ")
                .append(message.role().name().toLowerCase(Locale.ROOT));
        if (message.name() != null && !message.name().isBlank()) {
            summary.append("(name=").append(message.name()).append(")");
        }
        if (message.toolCallId() != null && !message.toolCallId().isBlank()) {
            summary.append("(toolCallId=").append(message.toolCallId()).append(")");
        }
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            String ids = message.toolCalls().stream()
                    .filter(Objects::nonNull)
                    .map(toolCall -> toolCall.name() + "#" + toolCall.id())
                    .collect(Collectors.joining(","));
            summary.append("(toolCalls=").append(ids).append(")");
        }
        summary.append(":").append(summarize(message.content(), 180));
        return summary.toString();
    }

    private static List<String> findToolCallProtocolViolations(List<AiChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<String> violations = new ArrayList<>();
        PendingToolResultBlock pendingBlock = null;
        for (int i = 0; i < messages.size(); i++) {
            AiChatMessage message = messages.get(i);
            if (message == null) {
                continue;
            }
            if (message.role() == AiChatMessage.Role.ASSISTANT
                    && message.toolCalls() != null
                    && !message.toolCalls().isEmpty()) {
                if (pendingBlock != null && !pendingBlock.isClosed()) {
                    violations.add("assistant tool-call block at index " + pendingBlock.assistantIndex()
                            + " is incomplete before assistant tool-call block at index " + i
                            + ": missing " + pendingBlock.pendingIds());
                }
                pendingBlock = PendingToolResultBlock.open(i, message.toolCalls());
                continue;
            }
            if (message.role() == AiChatMessage.Role.TOOL) {
                String toolCallId = message.toolCallId();
                if (toolCallId == null || toolCallId.isBlank()) {
                    violations.add("tool message at index " + i + " is missing toolCallId");
                    continue;
                }
                if (pendingBlock == null) {
                    violations.add("tool message at index " + i
                            + " has no open assistant tool-call block: " + toolCallId);
                    continue;
                }
                if (!pendingBlock.consume(toolCallId)) {
                    violations.add("tool message at index " + i
                            + " does not match current assistant tool-call block at index "
                            + pendingBlock.assistantIndex() + ": " + toolCallId);
                    continue;
                }
                continue;
            }
            if (pendingBlock != null && !pendingBlock.isClosed()) {
                violations.add("assistant tool-call block at index " + pendingBlock.assistantIndex()
                        + " is incomplete before " + message.role().name().toLowerCase(Locale.ROOT)
                        + " message at index " + i + ": missing " + pendingBlock.pendingIds());
            }
        }
        if (pendingBlock != null && !pendingBlock.isClosed()) {
            violations.add("assistant tool-call block at index " + pendingBlock.assistantIndex()
                    + " is incomplete at end of request: missing " + pendingBlock.pendingIds());
        }
        return violations;
    }

    private record PendingToolResultBlock(int assistantIndex, LinkedHashSet<String> pendingIds) {
        private static PendingToolResultBlock open(int assistantIndex, List<AiToolCall> toolCalls) {
            LinkedHashSet<String> ids = toolCalls.stream()
                    .filter(Objects::nonNull)
                    .map(AiToolCall::id)
                    .filter(id -> id != null && !id.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return new PendingToolResultBlock(assistantIndex, ids);
        }

        private boolean consume(String toolCallId) {
            return pendingIds.remove(toolCallId);
        }

        private boolean isClosed() {
            return pendingIds.isEmpty();
        }
    }

    private static String assistantMessageSummary(AiChatMessage message) {
        if (message == null) {
            return "";
        }
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("content", message.content());
        summary.put("toolCalls", message.toolCalls());
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(summary);
        } catch (Exception e) {
            return message.content();
        }
    }

    private static String summarize(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars)) + "...";
    }
}
