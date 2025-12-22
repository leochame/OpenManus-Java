package com.openmanus.agent.base;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.*;
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

        ChatModel chatModel;
        SystemMessage systemMessage;
        final Map<String, Map.Entry<ToolSpecification, ToolExecutor>> tools = new HashMap<>();

        /**
         * Sets the chat model to be used by the agent.
         * @param model The {@link ChatModel} instance.
         * @return The builder instance for chaining.
         */
        public B chatModel(ChatModel model) {
            this.chatModel = model;
            return result();
        }

        /**
         * Adds a pre-configured tool to the agent.
         * @param entry A map entry containing the tool's specification and its executor.
         * @return The builder instance for chaining.
         */
        public B tool(Map.Entry<ToolSpecification, ToolExecutor> entry) {
            tools.put(entry.getKey().name(), entry);
            return result();
        }

        /**
         * Scans an object for methods annotated with {@link dev.langchain4j.agent.tool.Tool}
         * and adds them as executable tools for the agent.
         *
         * @param objectWithTools The object containing tool methods.
         * @return The builder instance for chaining.
         */
        public B toolFromObject( Object objectWithTools ) {
            ToolSpecifications.toolSpecificationsFrom(objectWithTools).forEach(spec -> {
                Method method = findMethod(spec, objectWithTools);
                ToolExecutor executor = new DefaultToolExecutor(objectWithTools, method);
                tools.put(spec.name(), Map.entry(spec, executor));
            });
            return result();
        }

        /**
         * Finds the corresponding {@link Method} on a tool object that matches a {@link ToolSpecification}.
         * This implementation matches based on the method name and the number of parameters,
         * which is robust enough for most cases, including simple method overloading.
         *
         * @param spec The tool specification.
         * @param objectWithTools The object containing the method.
         * @return The found {@link Method}.
         * @throws IllegalStateException if no matching method is found.
         */
        private Method findMethod(ToolSpecification spec, Object objectWithTools) {
            // Find a method on the object that matches the tool specification's name and parameter count.
            // This is a robust way to handle most cases, including simple method overloading.
            return Arrays.stream(objectWithTools.getClass().getMethods())
                    .filter(method -> method.getName().equals(spec.name()))
                    .filter(method -> method.getParameterCount() == spec.parameters().properties().size())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            String.format("Method '%s' with %d parameters not found on class %s",
                                    spec.name(), spec.parameters().properties().size(), objectWithTools.getClass().getName())));
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

        /**
         * Sets the system message that provides instructions and context to the agent.
         * @param message The {@link SystemMessage}.
         * @return The builder instance for chaining.
         */
        public B systemMessage(SystemMessage message) {
            this.systemMessage = message;
            return result();
        }
    }

    private final ChatModel chatModel;
    private final SystemMessage systemMessage;
    private final Map<String, Map.Entry<ToolSpecification, ToolExecutor>> tools;
    private final List<ToolSpecification> toolSpecifications;
    private static final int MAX_ITERATIONS = 10; // To prevent infinite loops

    public AbstractAgentExecutor( Builder<B> builder ) {
        super( builder );
        this.chatModel = builder.chatModel;
        this.systemMessage = builder.systemMessage;
        this.tools = builder.tools;
        this.toolSpecifications = builder.tools.values().stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Executes the agent's ReAct loop to process a user request.
     * The method initiates a conversation with the language model, executes tools as directed by the model,
     * and continues this cycle until the model provides a final answer or the maximum number of iterations is reached.
     *
     * @param toolExecutionRequest The initial request that triggers the agent. The user's prompt is expected
     *                             to be in the 'context' field of the JSON arguments.
     * @param memoryId A unique identifier for the conversation memory, allowing the agent to maintain context
     *                 across multiple turns (if the underlying tools support it).
     * @return The final text response from the agent.
     * @throws RuntimeException if the agent exceeds the maximum number of iterations or if the model fails to respond.
     */
    @Override
    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        log.info("Starting agent execution with request: {}", toolExecutionRequest.toString());
        log.info("MemoryId: {}", memoryId != null ? memoryId.toString() : "null");

        // 1. Prepare initial messages
        List<ChatMessage> messages = new ArrayList<>();
        if (systemMessage != null) {
            messages.add(systemMessage);
        }
        messages.add(extractUserMessageFromRequest(toolExecutionRequest));

        // 2. Start the ReAct loop
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            log.info("Agent Iteration #{}", i + 1);

            // 3. Call the model with the current conversation history and available tools
            // The standard ChatModel interface uses .generate(), which returns a Response<AiMessage>.
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(toolSpecifications)
                    .build();
            ChatResponse response = chatModel.chat(chatRequest);
            if (response == null || response.aiMessage() == null) {
                throw new RuntimeException("LLM failed to generate a response.");
            }
            AiMessage aiMessage = response.aiMessage();
            messages.add(aiMessage); // Add AI's response to the history

            // 4. Analyze the response
            if (!aiMessage.hasToolExecutionRequests()) {
                // If the AI message does not contain a tool execution request, it's considered the final answer.
                log.info("Agent finished with a final answer.");
                return aiMessage.text(); // Task complete, return the final answer.
            }

            // 5. Execute the requested tool(s)
            List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
            for (ToolExecutionRequest request : requests) {
                log.info("Executing tool: {}", request.name());
                Map.Entry<ToolSpecification, ToolExecutor> toolEntry = tools.get(request.name());
                if (toolEntry != null) {
                    // Execute the tool and get the outcome.
                    String outcome = toolEntry.getValue().execute(request, memoryId);
                    // Create a message with the tool's result.
                    ToolExecutionResultMessage toolResultMessage = ToolExecutionResultMessage.from(request, outcome);
                    messages.add(toolResultMessage); // Add the tool result to the conversation history for the next iteration.
                }
            }
        }

        throw new RuntimeException("Agent exceeded maximum iterations (" + MAX_ITERATIONS + ")");
    }

    /**
     * Extracts the user's core message from the initial {@link ToolExecutionRequest}.
     * This implementation assumes the user's prompt is located within a 'context' field
     * in a JSON-formatted argument string. If parsing fails, it falls back to using the
     * entire argument string as the user message.
     *
     * @param toolExecutionRequest The initial request to the agent.
     * @return A {@link UserMessage} containing the extracted prompt.
     */
    private UserMessage extractUserMessageFromRequest(ToolExecutionRequest toolExecutionRequest) {
        String arguments = toolExecutionRequest.arguments();
        try {
            // Try to parse the arguments as JSON and extract the "context" field.
            var jsonArgs = com.google.gson.JsonParser.parseString(arguments).getAsJsonObject();
            arguments = jsonArgs.get("context").getAsString();
        } catch (Exception e) {
            // If parsing fails, assume the entire argument string is the user message.
            log.warn("Failed to parse arguments as JSON with 'context' field, using raw arguments. Error: {}", e.getMessage());
        }
        return UserMessage.from(arguments);
    }
}