package com.openmanus.java.config;

import com.openmanus.java.memory.ConversationBuffer;
import com.openmanus.java.memory.MemoryTool;
import com.openmanus.java.nodes.ActNode;
import com.openmanus.java.nodes.MemoryNode;
import com.openmanus.java.nodes.ObserveNode;
import com.openmanus.java.nodes.ReflectNode;
import com.openmanus.java.nodes.ThinkNode;
import com.openmanus.java.state.OpenManusAgentState;
import com.openmanus.java.tool.BrowserTool;
import com.openmanus.java.tool.FileTool;
import com.openmanus.java.tool.PythonTool;

import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.studio.springboot.AbstractLangGraphStudioConfig;
import org.bsc.langgraph4j.studio.springboot.LangGraphFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * LangGraph4j Studio 配置类
 * 
 * 为OpenManus项目提供可视化调试界面，支持：
 * - StateGraph流程可视化
 * - 实时执行状态监控
 * - 节点间状态传递查看
 * - 断点调试功能
 * - 图形化工作流编辑
 */
@Configuration
public class LangGraphStudioConfig extends AbstractLangGraphStudioConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(LangGraphStudioConfig.class);
    
    private final LangGraphFlow flow;
    
    public LangGraphStudioConfig(@Qualifier("chatModel") ChatModel chatModel,
                                PythonTool pythonTool,
                                FileTool fileTool,
                                BrowserTool browserTool,
                                MemoryTool memoryTool) throws GraphStateException {
        
        logger.info("初始化LangGraph4j Studio配置...");
        
        // 创建StateGraph工作流
        StateGraph<OpenManusAgentState> workflow = createOpenManusWorkflow(
            chatModel, pythonTool, fileTool, browserTool, memoryTool
        );
        
        // 生成Mermaid图表用于可视化
        var mermaidGraph = workflow.getGraph(GraphRepresentation.Type.MERMAID, "OpenManus ReAct Agent", false);
        logger.info("生成的Mermaid图表:\n{}", mermaidGraph.content());
        
        // 创建Studio流程配置
        this.flow = createStudioFlow(workflow);
        
        logger.info("LangGraph4j Studio配置初始化完成 - 访问地址: http://localhost:8089/");
    }
    
    /**
     * 创建OpenManus的StateGraph工作流
     */
    private StateGraph<OpenManusAgentState> createOpenManusWorkflow(
            ChatModel chatModel, 
            PythonTool pythonTool,
            FileTool fileTool, 
            BrowserTool browserTool,
            MemoryTool memoryTool) throws GraphStateException {
        
        // 创建节点实例
        ThinkNode thinkNode = new ThinkNode(chatModel);
        ActNode actNode = new ActNode(chatModel, pythonTool, fileTool, browserTool);
        ObserveNode observeNode = new ObserveNode(chatModel);
        // ReflectNode 使用 @Autowired 注入 ChatModel，需要手动设置
        ReflectNode reflectNode = new ReflectNode();
        // 通过反射设置 chatModel 字段
        try {
            java.lang.reflect.Field chatModelField = ReflectNode.class.getDeclaredField("chatModel");
            chatModelField.setAccessible(true);
            chatModelField.set(reflectNode, chatModel);
        } catch (Exception e) {
             logger.error("设置 ReflectNode 的 ChatModel 失败", e);
             throw new GraphStateException("Failed to inject ChatModel into ReflectNode: " + e.getMessage());
         }
        ConversationBuffer conversationBuffer = new ConversationBuffer();
        MemoryNode memoryNode = new MemoryNode(chatModel, memoryTool, conversationBuffer);
        
        // 定义条件边逻辑
        var routeNext = edge_async((OpenManusAgentState state) -> {
            logger.debug("路由决策 - 当前状态: {}, 迭代: {}", 
                        state.getCurrentState(), state.getIterationCount());
            
            // 检查是否有最终答案
            if (!state.getFinalAnswer().isEmpty()) {
                logger.info("发现最终答案，结束工作流");
                return END;
            }
            
            // 检查错误状态
            if (state.hasError()) {
                logger.warn("发现错误状态: {}", state.getError());
                return END;
            }
            
            // 检查最大迭代次数
            if (state.isMaxIterationsReached()) {
                logger.warn("达到最大迭代次数: {}", state.getMaxIterations());
                return END;
            }
            
            // 根据当前状态和迭代次数决定下一步
            String currentState = state.getCurrentState();
            int iteration = state.getIterationCount();
            
            switch (currentState) {
                case "start":
                    return "memory";  // 首先进行记忆管理
                case "memory_updated":
                    return "think";   // 记忆更新后开始思考
                case "thinking":
                    return "act";     // 思考后执行行动
                case "acting": 
                    return "observe"; // 行动后观察结果
                case "observing":
                    // 每3次迭代进行一次反思
                    if (iteration > 0 && iteration % 3 == 0) {
                        return "reflect";
                    }
                    return "think";   // 继续下一轮思考
                case "reflecting":
                    return "think";   // 反思后继续思考
                default:
                    return "think";   // 默认进入思考状态
            }
        });
        
        // 构建StateGraph
        try {
            return new StateGraph<OpenManusAgentState>(
                OpenManusAgentState.SCHEMA,
                initData -> new OpenManusAgentState(initData)
            )
                // 添加所有节点
                .addNode("memory", memoryNode)
                .addNode("think", thinkNode)
                .addNode("act", actNode)
                .addNode("observe", observeNode)
                .addNode("reflect", reflectNode)
                
                // 定义工作流
                .addEdge(START, "memory")  // 开始时先进行记忆管理
                
                // 添加条件边 - 所有节点都使用同一个路由逻辑
                .addConditionalEdges("memory", routeNext, Map.of(
                    "memory", "memory",
                    "think", "think",
                    "act", "act",
                    "observe", "observe",
                    "reflect", "reflect",
                    END, END
                ))
                .addConditionalEdges("think", routeNext, Map.of(
                    "memory", "memory",
                    "think", "think",
                    "act", "act",
                    "observe", "observe",
                    "reflect", "reflect",
                    END, END
                ))
                .addConditionalEdges("act", routeNext, Map.of(
                    "memory", "memory",
                    "think", "think",
                    "act", "act",
                    "observe", "observe",
                    "reflect", "reflect",
                    END, END
                ))
                .addConditionalEdges("observe", routeNext, Map.of(
                    "memory", "memory",
                    "think", "think",
                    "act", "act",
                    "observe", "observe",
                    "reflect", "reflect",
                    END, END
                ))
                .addConditionalEdges("reflect", routeNext, Map.of(
                    "memory", "memory",
                    "think", "think",
                    "act", "act",
                    "observe", "observe",
                    "reflect", "reflect",
                    END, END
                ));
                
        } catch (Exception e) {
            logger.error("创建StateGraph失败", e);
            throw new GraphStateException("Failed to create StateGraph: " + e.getMessage());
        }
    }
    
    /**
     * 创建Studio流程配置
     */
    private LangGraphFlow createStudioFlow(StateGraph<OpenManusAgentState> workflow) throws GraphStateException {
        return LangGraphFlow.builder()
                .title("OpenManus ReAct Agent - 可视化调试")
                .addInputStringArg("user_input", true, input -> input)  // 用户输入参数
                .addInputStringArg("session_id", false, sessionId -> sessionId != null ? sessionId : "default_session")  // 会话ID
                .addInputStringArg("max_iterations", false, maxIter -> {
                    if (maxIter != null) {
                        try {
                            return Integer.parseInt(maxIter.toString());
                        } catch (NumberFormatException e) {
                            return 10;
                        }
                    }
                    return 10;
                })  // 最大迭代次数
                .stateGraph(workflow)
                .compileConfig(CompileConfig.builder()
                        .checkpointSaver(new MemorySaver())  // 启用检查点保存
                        .releaseThread(true)                  // 启用线程释放
                        .build())
                .build();
    }
    
    @Override
    public LangGraphFlow getFlow() {
        return this.flow;
    }
}