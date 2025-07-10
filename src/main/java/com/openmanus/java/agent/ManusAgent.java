package com.openmanus.java.agent;

import com.openmanus.java.nodes.ActNode;
import com.openmanus.java.nodes.ObserveNode;
import com.openmanus.java.nodes.ThinkNode;
import com.openmanus.java.state.OpenManusAgentState;
import com.openmanus.java.tool.BrowserTool;
import com.openmanus.java.tool.FileTool;
import com.openmanus.java.tool.PythonTool;
import com.openmanus.java.tool.ReflectionTool;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * 基于 LangGraph4j StateGraph 架构的智能 Agent 实现
 * 支持完整的 ReAct 推理循环和反思机制
 */
@Component
public class ManusAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(ManusAgent.class);
    
    private final ChatModel chatModel;
    private final PythonTool pythonTool;
    private final FileTool fileTool;
    private final BrowserTool browserTool;
    private final ReflectionTool reflectionTool;
    private final ThinkNode thinkNode;
    private final ActNode actNode;
    private final ObserveNode observeNode;
    private final CompiledGraph<OpenManusAgentState> compiledGraph;
    
    @Autowired
    public ManusAgent(@Qualifier("chatModel") ChatModel chatModel,
                     PythonTool pythonTool,
                     FileTool fileTool,
                     BrowserTool browserTool,
                     ReflectionTool reflectionTool,
                     ThinkNode thinkNode,
                     ActNode actNode,
                     ObserveNode observeNode) {
        this.chatModel = chatModel;
        this.pythonTool = pythonTool;
        this.fileTool = fileTool;
        this.browserTool = browserTool;
        this.reflectionTool = reflectionTool;
        this.thinkNode = thinkNode;
        this.actNode = actNode;
        this.observeNode = observeNode;
        
        // 构建StateGraph工作流
        this.compiledGraph = buildStateGraph();
        
        logger.info("ManusAgent initialized with StateGraph architecture using ChatModel: {}", 
                   chatModel.getClass().getSimpleName());
    }
    
    /**
     * 构建基于StateGraph的ReAct工作流
     */
    private CompiledGraph<OpenManusAgentState> buildStateGraph() {
        try {
            // 定义条件边：根据状态决定下一步
            AsyncEdgeAction<OpenManusAgentState> routeNext = edge_async(state -> {
                // 检查是否有最终答案
                String finalAnswer = state.getFinalAnswer();
                if (!finalAnswer.isEmpty()) {
                    logger.info("发现最终答案，结束推理循环");
                    return END;
                }
                
                // 检查是否有错误
                if (state.hasError()) {
                    logger.warn("发现错误，结束推理: {}", state.getError());
                    return END;
                }
                
                // 检查是否达到最大迭代次数
                if (state.isMaxIterationsReached()) {
                    logger.warn("达到最大推理次数: {}", state.getMaxIterations());
                    return END;
                }
                
                // 根据当前状态决定下一步
                String currentState = state.getCurrentState();
                switch (currentState) {
                    case "start":
                    case "thinking":
                        return "think";
                    case "acting": 
                        return "act";
                    case "observing":
                        return "observe";
                    case "reflecting":
                        return "reflect";
                    default:
                        return "think"; // 默认进入思考状态
                }
            });
            
            // 构建StateGraph（这里使用简化的构造方式）
            StateGraph<OpenManusAgentState> graph = new StateGraph<OpenManusAgentState>(
                OpenManusAgentState.SCHEMA, 
                initData -> new OpenManusAgentState(initData)
            )
                // 添加节点
                .addNode("think", thinkNode)
                .addNode("act", actNode) 
                .addNode("observe", observeNode)
                .addNode("reflect", this::reflectNode)
                
                // 添加边
                .addEdge(START, "think")  // 从开始进入思考状态
                .addConditionalEdges("think", routeNext, Map.of(
                    "think", "think",
                    "act", "act", 
                    "observe", "observe",
                    "reflect", "reflect",
                    END, END
                ))
                .addConditionalEdges("act", routeNext, Map.of(
                    "think", "think",
                    "act", "act",
                    "observe", "observe", 
                    "reflect", "reflect",
                    END, END
                ))
                .addConditionalEdges("observe", routeNext, Map.of(
                    "think", "think",
                    "act", "act",
                    "observe", "observe",
                    "reflect", "reflect", 
                    END, END
                ))
                .addConditionalEdges("reflect", routeNext, Map.of(
                    "think", "think",
                    "act", "act",
                    "observe", "observe",
                    "reflect", "reflect",
                    END, END
                ));
            
            return graph.compile();
            
        } catch (Exception e) {
            logger.error("构建StateGraph失败", e);
            throw new RuntimeException("Failed to build StateGraph", e);
        }
    }
    
    /**
     * 反思节点实现
     */
    private CompletableFuture<Map<String, Object>> reflectNode(OpenManusAgentState state) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("开始反思阶段...");
                
                String taskId = state.getTaskId();
                if (taskId.isEmpty()) {
                    taskId = "task_" + System.currentTimeMillis();
                }
                
                // 记录任务到反思工具
                String reasoningSteps = state.getReasoningSteps().toString();
                String currentAnswer = state.getFinalAnswer();
                if (currentAnswer.isEmpty()) {
                    currentAnswer = "推理进行中...";
                }
                
                reflectionTool.recordTask(taskId, state.getUserInput(), 
                                        reasoningSteps, "ReAct推理", currentAnswer);
                
                // 进行反思
                String reflection = reflectionTool.reflectOnTask(taskId);
                
                logger.info("反思完成: {}", reflection);
                
                return Map.of(
                    "reflections", reflection,
                    "current_state", "thinking", // 反思后继续思考
                    "task_id", taskId
                );
                
            } catch (Exception e) {
                logger.error("反思节点执行失败", e);
                return Map.of(
                    "error", "反思过程中发生错误: " + e.getMessage(),
                    "current_state", "error"
                );
            }
        });
    }
    
    /**
     * 与Agent对话（使用StateGraph架构）
     */
    public Map<String, Object> chatWithCot(String userMessage) {
        logger.info("开始处理用户消息: {}", userMessage);
        
        try {
            // 创建初始状态
            Map<String, Object> initialData = Map.of(
                "user_input", userMessage,
                "session_id", "session_" + System.currentTimeMillis(),
                "task_id", "task_" + System.currentTimeMillis(),
                "current_state", "start",
                "max_iterations", 10
            );
            
            // 执行StateGraph工作流
            logger.info("启动StateGraph推理流程...");
            OpenManusAgentState finalState = null;
            
            // 流式执行图并获取最终状态
            for (var nodeOutput : compiledGraph.stream(initialData)) {
                finalState = nodeOutput.state();
                logger.debug("节点执行完成: {}, 当前状态: {}", 
                           nodeOutput.node(), finalState.getCurrentState());
            }
            
            if (finalState == null) {
                throw new RuntimeException("StateGraph执行未产生最终状态");
            }
            
            // 构建返回结果
            Map<String, Object> result = new HashMap<>();
            
            String finalAnswer = finalState.getFinalAnswer();
            if (finalAnswer.isEmpty() && finalState.hasError()) {
                finalAnswer = "抱歉，处理您的问题时出现了错误：" + finalState.getError();
            } else if (finalAnswer.isEmpty()) {
                finalAnswer = "推理过程已完成，但未能得出明确答案。";
            }
            
            result.put("answer", finalAnswer);
            result.put("content", finalAnswer);
            result.put("cot", extractCotSteps(finalState));
            result.put("reasoning_steps", finalState.getReasoningSteps());
            result.put("tool_calls", finalState.getToolCalls());
            result.put("observations", finalState.getObservations());
            result.put("reflections", finalState.getReflections());
            result.put("iteration_count", finalState.getIterationCount());
            
            logger.info("推理完成，迭代次数: {}, 最终答案: {}", 
                       finalState.getIterationCount(), finalAnswer);
            
            return result;
            
        } catch (Exception e) {
            logger.error("处理用户消息时出现错误", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("answer", "抱歉，处理您的问题时出现了错误：" + e.getMessage());
            errorResult.put("content", errorResult.get("answer"));
            errorResult.put("cot", new String[]{"错误: " + e.getMessage()});
            return errorResult;
        }
    }
    
    /**
     * 从最终状态中提取COT步骤
     */
    private String[] extractCotSteps(OpenManusAgentState finalState) {
        return finalState.getReasoningSteps().stream()
                .map(step -> step.get("type") + ": " + step.get("content"))
                .toArray(String[]::new);
    }
    
    /**
     * 获取Agent状态信息
     */
    public Map<String, Object> getAgentInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("architecture", "LangGraph4j StateGraph");
        info.put("model", chatModel.getClass().getSimpleName());
        info.put("nodes", new String[]{"think", "act", "observe", "reflect"});
        info.put("tools", new String[]{"PythonTool", "FileTool", "BrowserTool", "ReflectionTool"});
        return info;
    }
}