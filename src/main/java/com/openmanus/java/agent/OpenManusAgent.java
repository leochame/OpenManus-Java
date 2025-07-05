package com.openmanus.java.agent;

import com.openmanus.java.state.OpenManusAgentState;
import com.openmanus.java.tool.ToolRegistry;
import com.openmanus.java.config.OpenManusProperties;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.langchain4j.serializer.jackson.LC4jJacksonStateSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * OpenManus Agent - 基于 langgraph4j 的现代化 Agent 实现
 * 
 * 这个类实现了我们开发计划中定义的核心 Agent 架构：
 * - 使用 StateGraph 管理工作流
 * - 集成 LangChain4j 的函数调用功能
 * - 支持工具执行和状态管理
 */
@Component
public class OpenManusAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenManusAgent.class);
    
    // 节点名称常量
    private static final String MODEL_CALLER_NODE = "model_caller";
    private static final String ACTION_EXECUTOR_NODE = "action_executor";
    private static final String ROUTER_NODE = "router";
    
    private final ChatLanguageModel chatModel;
    private final ToolRegistry toolRegistry;
    private final OpenManusProperties properties;
    private final CompiledGraph<OpenManusAgentState> compiledGraph;
    
    /**
     * 构造函数
     */
    public OpenManusAgent(ChatLanguageModel chatModel, 
                         ToolRegistry toolRegistry, 
                         OpenManusProperties properties) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.compiledGraph = buildGraph();
        
        logger.info("OpenManus Agent initialized with {} tools", toolRegistry.getToolCount());
    }
    
    /**
     * 构建状态图
     */
    private CompiledGraph<OpenManusAgentState> buildGraph() {
        try {
            // 创建状态工厂
            AgentStateFactory<OpenManusAgentState> stateFactory = OpenManusAgentState::new;
            
            // 创建状态序列化器 - 使用 Jackson 序列化器以支持 LangChain4j 消息类型
            LC4jJacksonStateSerializer<OpenManusAgentState> serializer = 
                new LC4jJacksonStateSerializer<>(stateFactory);
            
            // 创建状态图
            StateGraph<OpenManusAgentState> stateGraph = new StateGraph<>(
                OpenManusAgentState.SCHEMA, 
                serializer
            );
            
            // 添加节点
            stateGraph.addNode(MODEL_CALLER_NODE, createModelCallerNode());
            stateGraph.addNode(ACTION_EXECUTOR_NODE, createActionExecutorNode());
            
            // 添加边
            stateGraph.addEdge(START, MODEL_CALLER_NODE);
            stateGraph.addEdge(ACTION_EXECUTOR_NODE, MODEL_CALLER_NODE);
            
            // 添加条件边 - 路由器
            stateGraph.addConditionalEdges(
                MODEL_CALLER_NODE,
                createRouterEdge(),
                Map.of(
                    "continue", ACTION_EXECUTOR_NODE,
                    "end", END
                )
            );
            
            return stateGraph.compile();
        } catch (Exception e) {
            logger.error("构建状态图失败", e);
            throw new RuntimeException("构建状态图失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建模型调用节点
     * 这个节点负责调用 LLM 进行思考，生成最终答案或函数调用请求
     */
    private AsyncNodeAction<OpenManusAgentState> createModelCallerNode() {
        return (OpenManusAgentState state) -> {
            logger.debug("执行 MODEL_CALLER 节点");
            
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // 更新状态为思考中
                    state.updateAgentStatus(OpenManusAgentState.Status.THINKING);
                    
                    // 获取消息历史
                    List<ChatMessage> messages = state.getMessages();
                    
                    // 准备工具规格
                    List<ToolSpecification> toolSpecs = toolRegistry.getToolSpecifications();
                    
                    // 调用 LLM
                    Response<AiMessage> response;
                    if (toolSpecs.isEmpty()) {
                        response = chatModel.generate(messages);
                    } else {
                        response = chatModel.generate(messages, toolSpecs);
                    }
                    
                    AiMessage aiMessage = response.content();
                    
                    // 创建状态更新
                    Map<String, Object> updates = new HashMap<>();
                    
                    // 添加 AI 消息到历史
                    List<ChatMessage> updatedMessages = new ArrayList<>(messages);
                    updatedMessages.add(aiMessage);
                    updates.put(OpenManusAgentState.MESSAGES_KEY, updatedMessages);
                    
                    // 检查是否有工具调用
                    if (aiMessage.hasToolExecutionRequests()) {
                        updates.put(OpenManusAgentState.AGENT_STATUS_KEY, OpenManusAgentState.Status.ACTING);
                        logger.debug("LLM 请求执行 {} 个工具", aiMessage.toolExecutionRequests().size());
                    } else {
                        updates.put(OpenManusAgentState.AGENT_STATUS_KEY, OpenManusAgentState.Status.FINISHED);
                        logger.debug("LLM 生成最终答案，任务完成");
                    }
                    
                    return updates;
                    
                } catch (Exception e) {
                    logger.error("MODEL_CALLER 节点执行失败", e);
                    return Map.of(
                        OpenManusAgentState.AGENT_STATUS_KEY, OpenManusAgentState.Status.ERROR,
                        OpenManusAgentState.ERROR_INFO_KEY, "模型调用失败: " + e.getMessage()
                    );
                }
            });
        };
    }
    
    /**
     * 创建动作执行节点
     * 这个节点负责执行 LLM 请求的函数调用（工具）
     */
    private AsyncNodeAction<OpenManusAgentState> createActionExecutorNode() {
        return (OpenManusAgentState state) -> {
            logger.debug("执行 ACTION_EXECUTOR 节点");
            
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // 更新状态为执行中
                    state.updateAgentStatus(OpenManusAgentState.Status.ACTING);
                    
                    List<ChatMessage> messages = state.getMessages();
                    if (messages.isEmpty()) {
                        logger.warn("消息历史为空，无法执行工具");
                        return Map.of(
                            OpenManusAgentState.AGENT_STATUS_KEY, OpenManusAgentState.Status.ERROR,
                            OpenManusAgentState.ERROR_INFO_KEY, "消息历史为空"
                        );
                    }
                    
                    // 获取最后一条消息（应该是包含工具调用的 AI 消息）
                    ChatMessage lastMessage = messages.get(messages.size() - 1);
                    if (!(lastMessage instanceof AiMessage)) {
                        logger.warn("最后一条消息不是 AI 消息，无法执行工具");
                        return Map.of(
                            OpenManusAgentState.AGENT_STATUS_KEY, OpenManusAgentState.Status.ERROR,
                            OpenManusAgentState.ERROR_INFO_KEY, "最后一条消息不是 AI 消息"
                        );
                    }
                    
                    AiMessage aiMessage = (AiMessage) lastMessage;
                    if (!aiMessage.hasToolExecutionRequests()) {
                        logger.warn("AI 消息中没有工具调用请求");
                        return Map.of(
                            OpenManusAgentState.AGENT_STATUS_KEY, OpenManusAgentState.Status.ERROR,
                            OpenManusAgentState.ERROR_INFO_KEY, "没有工具调用请求"
                        );
                    }
                    
                    // 执行所有工具调用
                    List<ChatMessage> updatedMessages = new ArrayList<>(messages);
                    List<OpenManusAgentState.ToolCall> toolCalls = new ArrayList<>();
                    
                    for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                        logger.debug("执行工具: {}", request.name());
                        
                        OpenManusAgentState.ToolCall toolCall = new OpenManusAgentState.ToolCall(
                            request.name(), 
                            request.arguments() != null ? 
                                Map.of("arguments", request.arguments()) : 
                                new HashMap<>()
                        );
                        
                        try {
                            // 执行工具
                            Object result = toolRegistry.execute(request.name(), request.arguments());
                            String resultStr = result != null ? result.toString() : "";
                            toolCall.setResult(resultStr);
                            toolCall.setSuccess(true);
                            
                            // 创建工具执行结果消息
                            ToolExecutionResultMessage resultMessage = ToolExecutionResultMessage.from(
                                request.id(), 
                                request.name(), 
                                resultStr
                            );
                            updatedMessages.add(resultMessage);
                            
                            logger.debug("工具 {} 执行成功", request.name());
                            
                        } catch (Exception e) {
                            logger.error("工具 {} 执行失败", request.name(), e);
                            toolCall.setSuccess(false);
                            toolCall.setErrorMessage(e.getMessage());
                            
                            // 创建错误结果消息
                            ToolExecutionResultMessage errorMessage = ToolExecutionResultMessage.from(
                                request.id(), 
                                request.name(), 
                                "工具执行失败: " + e.getMessage()
                            );
                            updatedMessages.add(errorMessage);
                        }
                        
                        toolCalls.add(toolCall);
                    }
                    
                    // 创建状态更新
                    Map<String, Object> updates = new HashMap<>();
                    updates.put(OpenManusAgentState.MESSAGES_KEY, updatedMessages);
                    updates.put(OpenManusAgentState.TOOL_CALLS_KEY, toolCalls);
                    updates.put(OpenManusAgentState.AGENT_STATUS_KEY, OpenManusAgentState.Status.OBSERVING);
                    
                    logger.debug("工具执行完成，执行了 {} 个工具", toolCalls.size());
                    
                    return updates;
                    
                } catch (Exception e) {
                    logger.error("ACTION_EXECUTOR 节点执行失败", e);
                    return Map.of(
                        OpenManusAgentState.AGENT_STATUS_KEY, OpenManusAgentState.Status.ERROR,
                        OpenManusAgentState.ERROR_INFO_KEY, "工具执行失败: " + e.getMessage()
                    );
                }
            });
        };
    }
    
    /**
     * 创建路由器边
     * 这个边决定下一个节点是继续执行工具还是结束
     */
    private AsyncEdgeAction<OpenManusAgentState> createRouterEdge() {
        return (OpenManusAgentState state) -> {
            logger.debug("执行路由器决策");
            
            return CompletableFuture.supplyAsync(() -> {
                try {
                    List<ChatMessage> messages = state.getMessages();
                    if (messages.isEmpty()) {
                        return "end";
                    }
                    
                    // 获取最后一条消息
                    ChatMessage lastMessage = messages.get(messages.size() - 1);
                    
                    // 如果最后一条消息是 AI 消息且包含工具调用，则继续执行
                    if (lastMessage instanceof AiMessage) {
                        AiMessage aiMessage = (AiMessage) lastMessage;
                        if (aiMessage.hasToolExecutionRequests()) {
                            logger.debug("路由器决策: 继续执行工具");
                            return "continue";
                        }
                    }
                    
                    // 检查状态
                    OpenManusAgentState.Status status = state.getAgentStatus();
                    if (status == OpenManusAgentState.Status.FINISHED || 
                        status == OpenManusAgentState.Status.ERROR) {
                        logger.debug("路由器决策: 结束执行 (状态: {})", status);
                        return "end";
                    }
                    
                    // 默认结束
                    logger.debug("路由器决策: 默认结束");
                    return "end";
                    
                } catch (Exception e) {
                    logger.error("路由器决策失败", e);
                    return "end";
                }
            });
        };
    }
    
    /**
     * 执行 Agent 任务
     * 
     * @param userMessage 用户消息
     * @return 执行结果
     */
    public CompletableFuture<String> execute(String userMessage) {
        logger.info("开始执行 Agent 任务: {}", userMessage);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证输入
                if (userMessage == null || userMessage.trim().isEmpty()) {
                    return "请提供有效的输入消息";
                }
                
                // 创建初始状态
                Map<String, Object> initialData = new HashMap<>();
                initialData.put(OpenManusAgentState.MESSAGES_KEY, 
                    List.of(UserMessage.from(userMessage)));
                initialData.put(OpenManusAgentState.CURRENT_TASK_KEY, userMessage);
                initialData.put(OpenManusAgentState.AGENT_STATUS_KEY, OpenManusAgentState.Status.IDLE.name());
                
                // 执行图
                Optional<OpenManusAgentState> result = compiledGraph.invoke(initialData);
                if (!result.isPresent()) {
                    return "Agent 执行失败：无法获取执行结果";
                }
                OpenManusAgentState finalState = result.get();
                
                // 提取最终答案
                List<ChatMessage> messages = finalState.getMessages();
                if (!messages.isEmpty()) {
                    ChatMessage lastMessage = messages.get(messages.size() - 1);
                    if (lastMessage instanceof AiMessage) {
                        AiMessage aiMessage = (AiMessage) lastMessage;
                        String response = aiMessage.text();
                        
                        logger.info("Agent 任务完成，状态: {}", finalState.getAgentStatus());
                        return response != null ? response : "任务完成，但没有生成回复";
                    }
                }
                
                return "任务完成，但没有找到最终答案";
                
            } catch (Exception e) {
                logger.error("Agent 执行失败", e);
                return "Agent 执行失败: " + e.getMessage();
            }
        });
    }
    
    /**
     * 获取已编译的图（用于调试和监控）
     */
    public CompiledGraph<OpenManusAgentState> getCompiledGraph() {
        return compiledGraph;
    }
} 