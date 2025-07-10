package com.openmanus.java.workflow;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.EdgeAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.openmanus.java.state.OpenManusAgentState;
import com.openmanus.java.nodes.ThinkNode;
import com.openmanus.java.nodes.ActNode;
import com.openmanus.java.nodes.ObserveNode;
import com.openmanus.java.nodes.ReflectNode;
import com.openmanus.java.nodes.MemoryNode;

import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * React Agent工作流 - 基于langgraph4j StateGraph实现的完整React推理框架
 * 
 * 该工作流实现了Think->Act->Observe->Reflect循环，支持：
 * - 多轮推理迭代
 * - 条件边路由
 * - 错误处理和重试
 * - 内存管理集成
 * - 反思和自我优化
 */
@Component
public class ReactAgentWorkflow {
    private static final Logger logger = LoggerFactory.getLogger(ReactAgentWorkflow.class);
    
    @Autowired
    private ThinkNode thinkNode;
    
    @Autowired
    private ActNode actNode;
    
    @Autowired
    private ObserveNode observeNode;
    
    @Autowired
    private ReflectNode reflectNode;
    
    @Autowired
    private MemoryNode memoryNode;
    
    /**
     * 构建React Agent的StateGraph工作流
     */
    public CompiledGraph<OpenManusAgentState> buildWorkflow() {
        try {
            logger.info("开始构建ReactAgent StateGraph工作流...");
            
            // 创建StateGraph
            StateGraph<OpenManusAgentState> stateGraph = new StateGraph<>(OpenManusAgentState.SCHEMA, OpenManusAgentState::new);
            
            return stateGraph
                // 添加节点
                .addNode("think", thinkNode)
                .addNode("act", actNode)
                .addNode("observe", observeNode)
                .addNode("reflect", reflectNode)
                .addNode("memory", memoryNode)
                
                // 设置入口点
                .addEdge(StateGraph.START, "think")
                
                // 添加条件边 - Think节点的路由逻辑
                .addConditionalEdges("think", 
                    edge_async(this::routeFromThink),
                    Map.of(
                        "act", "act",
                        "finished", StateGraph.END,
                        "error", StateGraph.END
                    )
                )
                
                // 添加条件边 - Act节点的路由逻辑  
                .addConditionalEdges("act",
                    edge_async(this::routeFromAct), 
                    Map.of(
                        "observe", "observe",
                        "error", StateGraph.END,
                        "finished", StateGraph.END
                    )
                )
                
                // 添加条件边 - Observe节点的路由逻辑
                .addConditionalEdges("observe",
                    edge_async(this::routeFromObserve),
                    Map.of(
                        "reflect", "reflect", 
                        "think", "think",
                        "finished", StateGraph.END,
                        "error", StateGraph.END
                    )
                )
                
                // 添加条件边 - Reflect节点的路由逻辑
                .addConditionalEdges("reflect",
                    edge_async(this::routeFromReflect),
                    Map.of(
                        "memory", "memory",
                        "think", "think", 
                        "finished", StateGraph.END,
                        "error", StateGraph.END
                    )
                )
                
                // Memory节点总是回到Think重新开始
                .addEdge("memory", "think")
                
                // 编译工作流
                .compile();
                
        } catch (Exception e) {
            logger.error("构建ReactAgent工作流失败", e);
            throw new RuntimeException("Failed to build ReactAgent workflow", e);
        }
    }
    
    /**
     * Think节点的路由逻辑
     */
    private String routeFromThink(OpenManusAgentState state) throws Exception {
        try {
            // 检查是否有错误
            if (state.hasError()) {
                logger.warn("Think节点检测到错误，路由到error");
                return "error";
            }
            
            // 检查是否达到最大迭代次数
            if (state.isMaxIterationsReached()) {
                logger.info("达到最大迭代次数，路由到finished");
                return "finished";
            }
            
            // 检查是否有最终答案
            if (!state.getFinalAnswer().isEmpty()) {
                logger.info("已有最终答案，路由到finished");
                return "finished";
            }
            
            // 检查是否需要执行工具调用
            if (!state.getToolCalls().isEmpty()) {
                logger.info("需要执行工具调用，路由到act");
                return "act";
            }
            
            // 默认继续思考
            logger.info("继续思考，路由到think");
            return "think";
            
        } catch (Exception e) {
            logger.error("Think节点路由逻辑异常", e);
            return "error";
        }
    }
    
    /**
     * Act节点的路由逻辑
     */
    private String routeFromAct(OpenManusAgentState state) throws Exception {
        try {
            // 检查是否有错误
            if (state.hasError()) {
                logger.warn("Act节点检测到错误，路由到error");
                return "error";
            }
            
            // 检查工具调用是否完成
            if (!state.getObservations().isEmpty()) {
                logger.info("工具调用完成，路由到observe");
                return "observe";
            }
            
            // 默认继续执行
            logger.info("继续执行，路由到act");
            return "act";
            
        } catch (Exception e) {
            logger.error("Act节点路由逻辑异常", e);
            return "error";
        }
    }
    
    /**
     * Observe节点的路由逻辑
     */
    private String routeFromObserve(OpenManusAgentState state) throws Exception {
        try {
            // 检查是否有错误
            if (state.hasError()) {
                logger.warn("Observe节点检测到错误，路由到error");
                return "error";
            }
            
            // 检查是否需要反思
            if (state.getIterationCount() > 3) {
                logger.info("迭代次数较多，需要反思，路由到reflect");
                return "reflect";
            }
            
            // 检查是否有最终答案
            if (!state.getFinalAnswer().isEmpty()) {
                logger.info("已有最终答案，路由到finished");
                return "finished";
            }
            
            // 默认继续思考
            logger.info("继续思考，路由到think");
            return "think";
            
        } catch (Exception e) {
            logger.error("Observe节点路由逻辑异常", e);
            return "error";
        }
    }
    
    /**
     * Reflect节点的路由逻辑
     */
    private String routeFromReflect(OpenManusAgentState state) throws Exception {
        try {
            // 检查是否有错误
            if (state.hasError()) {
                logger.warn("Reflect节点检测到错误，路由到error");
                return "error";
            }
            
            // 检查是否需要更新内存
            if (!state.getReflections().isEmpty()) {
                logger.info("需要更新内存，路由到memory");
                return "memory";
            }
            
            // 检查是否有最终答案
            if (!state.getFinalAnswer().isEmpty()) {
                logger.info("已有最终答案，路由到finished");
                return "finished";
            }
            
            // 默认继续思考
            logger.info("继续思考，路由到think");
            return "think";
            
        } catch (Exception e) {
            logger.error("Reflect节点路由逻辑异常", e);
            return "error";
        }
    }
    
    /**
     * 检查状态是否有错误
     */
    private boolean hasError(OpenManusAgentState state) {
        // 简单的错误检查逻辑
        String currentStep = state.getCurrentStep();
        return currentStep != null && currentStep.contains("error");
    }
    
    /**
     * 提取观察内容
     */
    private String extractObservationContents(List<String> observations) {
        if (observations == null || observations.isEmpty()) {
            return "";
        }
        
        StringBuilder content = new StringBuilder();
        for (String obs : observations) {
            content.append(obs).append("\n");
        }
        return content.toString().trim();
    }
    
    /**
     * 生成工作流状态摘要
     */
    public String generateWorkflowSummary(OpenManusAgentState state) {
        StringBuilder summary = new StringBuilder();
        summary.append("=== ReactAgent工作流状态摘要 ===\n");
        summary.append(state.getReactSummary());
        summary.append("\n=== 工作流统计 ===\n");
        summary.append("消息总数: ").append(state.getMessages().size()).append("\n");
        summary.append("工具调用数: ").append(state.getToolCalls().size()).append("\n");
        summary.append("观察记录: ").append(extractObservationContents(state.getObservations())).append("\n");
        summary.append("反思记录数: ").append(state.getReflections().size()).append("\n");
        
        return summary.toString();
    }
} 