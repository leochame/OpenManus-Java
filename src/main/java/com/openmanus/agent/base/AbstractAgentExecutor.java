package com.openmanus.agent.base;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractAgentExecutor<B extends AbstractAgentExecutor.Builder<B>> extends AbstractAgent<B> {

    public static abstract class Builder<B extends AbstractAgentExecutor.Builder<B>> extends AbstractAgent.Builder<B> {

        ChatModel chatModel;
        SystemMessage systemMessage;
        final Map<String, Map.Entry<ToolSpecification, ToolExecutor>> tools = new HashMap<>();

        public B chatModel(ChatModel model) {
            this.chatModel = model;
            return result();
        }

        public B tool(Map.Entry<ToolSpecification, ToolExecutor> entry) {
            tools.put(entry.getKey().name(), entry);
            return result();
        }

        public B toolFromObject( Object objectWithTools ) {
            dev.langchain4j.agent.tool.Tools.toolSpecificationsFrom(objectWithTools).forEach(spec -> {
                ToolExecutor executor = new dev.langchain4j.service.tool.DefaultToolExecutor(objectWithTools, spec.name());
                tools.put(spec.name(), Map.entry(spec, executor));
            });
            return result();
        }

        public B toolsFromObjects( Object... objectsWithTools ) {
            for (Object tool : objectsWithTools) {
                toolFromObject(tool);
            }
            return result();
        }

        public B systemMessage(SystemMessage message) {
            this.systemMessage = message;
            return result();
        }
    }

    private final ChatModel chatModel;
    private final SystemMessage systemMessage;
    private final Map<String, Map.Entry<ToolSpecification, ToolExecutor>> tools;
    private final List<ToolSpecification> toolSpecifications;
    private static final int MAX_ITERATIONS = 10; // 防止无限循环

    public AbstractAgentExecutor( Builder<B> builder ) {
        super( builder );
        this.chatModel = builder.chatModel;
        this.systemMessage = builder.systemMessage;
        this.tools = builder.tools;
        this.toolSpecifications = builder.tools.values().stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        log.info("大模型调用执行：————————————————————————————：{}",toolExecutionRequest.toString());
        log.info("MemoryId: {}", memoryId != null ? memoryId.toString() : "null");

        // 1. 准备初始消息
        List<ChatMessage> messages = new ArrayList<>();
        if (systemMessage != null) {
            messages.add(systemMessage);
        }
        messages.add(extractUserMessageFromRequest(toolExecutionRequest));

        // 2. 开始 ReAct 循环
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            log.info("Agent Iteration #{}", i + 1);

            // 3. 调用模型
            AiMessage aiMessage = chatModel.chat(messages).aiMessage();
            messages.add(aiMessage);

            // 4. 分析响应
            if (!aiMessage.hasToolExecutionRequests()) {
                log.info("Agent finished with a final answer.");
                return aiMessage.text(); // 任务完成，返回最终答案
            }

            // 5. 执行工具
            List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
            for (ToolExecutionRequest request : requests) {
                log.info("Executing tool: {}", request.name());
                Map.Entry<ToolSpecification, ToolExecutor> toolEntry = tools.get(request.name());
                if (toolEntry != null) {
                    String outcome = toolEntry.getValue().execute(request, memoryId);
                    ToolExecutionResultMessage toolResultMessage = ToolExecutionResultMessage.from(request, outcome);
                    messages.add(toolResultMessage); // 将工具结果加入历史
                }
            }
        }

        throw new RuntimeException("Agent exceeded maximum iterations (" + MAX_ITERATIONS + ")");
    }

    private UserMessage extractUserMessageFromRequest(ToolExecutionRequest toolExecutionRequest) {
        String arguments = toolExecutionRequest.arguments();
        try {
            // 尝试将参数解析为JSON，并提取 "context" 字段
            var jsonArgs = com.google.gson.JsonParser.parseString(arguments).getAsJsonObject();
            arguments = jsonArgs.get("context").getAsString();
        } catch (Exception e) {
            // 如果解析失败，则假定整个参数字符串就是用户消息
            log.warn("Failed to parse arguments as JSON with 'context' field, using raw arguments. Error: {}", e.getMessage());
        }
        return UserMessage.from(arguments);
    }
}