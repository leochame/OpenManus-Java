package com.openmanus.java.agent.impl.supervisor;

import com.openmanus.java.agent.base.AbstractAgentExecutor;
import com.openmanus.java.agent.tool.AgentToolCatalog;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.SystemMessage;
import org.bsc.langgraph4j.GraphStateException;

import java.util.ArrayList;
import java.util.Map;

/**
 * 监督者智能体 - Think-Do-Reflect系统的总协调者
 * 
 * 核心功能：
 * 1. 管理"想、做、回"的工作流程
 * 2. 通过工具调用选择合适的智能体
 * 3. 控制循环执行和状态管理
 * 4. 防止无限循环
 */
public class SupervisorAgent extends AbstractAgentExecutor<SupervisorAgent.Builder> {
    
    // 防止无限循环的最大循环次数
    private static final int MAX_CYCLES = 5;
    
    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {
        
        private AgentToolCatalog agentToolCatalog;
        
        public Builder agentToolCatalog(AgentToolCatalog agentToolCatalog) {
            this.agentToolCatalog = agentToolCatalog;
            return this;
        }
        
        public SupervisorAgent build() throws GraphStateException {
            this.name("supervisor")
                .description("负责协调整个智能体工作流程的智能体")
                .singleParameter("用户请求和执行上下文")
                .systemMessage(SystemMessage.from("""
                    你是智能体系统的总协调者，负责管理"想、做、回"的工作流程：
                    1. 想：使用think工具分析任务并规划
                    2. 做：根据需求使用适当的执行工具(search, executeCode, handleFile)
                    3. 回：使用reflect工具评估结果，决定是否需要继续循环
                    
                    你的主要职责是：
                    - 分析当前状态，选择合适的工具
                    - 管理执行循环，确保任务最终完成
                    - 监控执行进度，防止无限循环
                    
                    工作流程：
                    1. 第一轮：调用think工具来规划任务
                    2. 然后根据计划调用适当的执行工具
                    3. 执行完成后调用reflect工具评估结果
                    4. 根据评估结果决定是否需要重新规划执行
                    
                    重要提醒：
                    - 一定要跟踪cycle_count和phase字段，防止无限循环
                    - 当cycle_count达到最大值时，必须结束循环
                    - 每次调用reflect后，根据返回结果决定下一步行动
                    - 如果reflect返回"任务已完成"，则结束流程
                    - 如果reflect返回"任务未完成"，则重新调用think工具
                    """));
            
            // 集成智能体工具
            if (agentToolCatalog != null) {
                for (Object tool : agentToolCatalog.getTools()) {
                    this.toolFromObject(tool);
                }
            }
            
            return new SupervisorAgent(this);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public SupervisorAgent(Builder builder) throws GraphStateException {
        super(builder);
    }
    
    @Override
    public String execute(ToolExecutionRequest request, Object context) {
        // 初始化并管理状态
        Map<String, Object> state = (Map<String, Object>) context;
        
        // 首次执行时初始化状态
        if (!state.containsKey("original_request")) {
            state.put("original_request", request.arguments());
            state.put("cycle_count", 0);
            state.put("phase", "thinking");
            state.put("execution_history", new ArrayList<>());
        }
        
        // 检查循环次数是否超限
        int cycleCount = (int) state.getOrDefault("cycle_count", 0);
        if (cycleCount >= MAX_CYCLES) {
            state.put("phase", "completed");
            state.put("final_result", "已达到最大循环次数，返回当前最佳结果。");
            return "已达到最大循环次数(" + MAX_CYCLES + ")，返回当前最佳结果。";
        }
        
        // 调用内部AgentExecutor处理请求
        // 注意：这里不需要自己判断和路由，language model会自动选择合适的工具
        return super.execute(request, context);
    }
}
