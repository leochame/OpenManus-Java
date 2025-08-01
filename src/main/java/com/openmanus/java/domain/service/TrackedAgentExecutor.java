package com.openmanus.java.domain.service;

import com.openmanus.java.agent.base.AbstractAgent;
import com.openmanus.java.domain.model.AgentExecutionEvent;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 带执行跟踪功能的Agent执行器装饰器
 * 包装现有的Agent，添加执行状态跟踪功能
 */
@Component
@Slf4j
public class TrackedAgentExecutor implements ToolExecutor {
    
    private final AgentExecutionTracker executionTracker;
    private final ToolExecutor delegate;
    private final String agentName;
    private final String agentType;
    
    @Autowired
    public TrackedAgentExecutor(AgentExecutionTracker executionTracker) {
        this.executionTracker = executionTracker;
        this.delegate = null;
        this.agentName = null;
        this.agentType = null;
    }
    
    /**
     * 私有构造函数，用于创建具体的跟踪实例
     */
    private TrackedAgentExecutor(AgentExecutionTracker executionTracker, ToolExecutor delegate, String agentName, String agentType) {
        this.executionTracker = executionTracker;
        this.delegate = delegate;
        this.agentName = agentName;
        this.agentType = agentType;
    }
    
    /**
     * 包装Agent为带跟踪功能的执行器
     */
    public <B extends AbstractAgent.Builder<B>> Map.Entry<ToolSpecification, ToolExecutor> wrapAgent(AbstractAgent<B> agent) {
        Map.Entry<ToolSpecification, ToolExecutor> originalTool = agent.asTool();
        
        // 创建带跟踪功能的执行器
        TrackedAgentExecutor trackedExecutor = new TrackedAgentExecutor(
            executionTracker,
            originalTool.getValue(),
            agent.name(),
            agent.getClass().getSimpleName()
        );
        
        return Map.entry(originalTool.getKey(), trackedExecutor);
    }
    
    @Override
    public String execute(ToolExecutionRequest request, Object context) {
        if (delegate == null) {
            throw new IllegalStateException("TrackedAgentExecutor not properly initialized. Use wrapAgent() method.");
        }
        
        // 从上下文中提取会话ID
        String sessionId = extractSessionId(context);
        
        // 记录Agent开始执行
        executionTracker.startAgentExecution(sessionId, agentName, agentType, request.arguments());
        
        try {
            // 执行原始Agent
            String result = delegate.execute(request, context);
            
            // 记录Agent执行成功
            executionTracker.endAgentExecution(sessionId, agentName, agentType, result, AgentExecutionEvent.ExecutionStatus.SUCCESS);
            
            return result;
            
        } catch (Exception e) {
            // 记录Agent执行错误
            executionTracker.recordAgentError(sessionId, agentName, agentType, e.getMessage());
            
            // 重新抛出异常
            throw e;
        }
    }
    
    /**
     * 从上下文中提取会话ID
     * 如果上下文中没有会话ID，则生成一个新的
     */
    private String extractSessionId(Object context) {
        if (context instanceof Map) {
            Map<?, ?> contextMap = (Map<?, ?>) context;
            Object sessionId = contextMap.get("sessionId");
            if (sessionId != null) {
                return sessionId.toString();
            }
            
            // 如果没有会话ID，生成一个新的并添加到上下文中
            String newSessionId = UUID.randomUUID().toString();
            if (contextMap instanceof Map<String, Object>) {
                ((Map<String, Object>) contextMap).put("sessionId", newSessionId);
            }
            return newSessionId;
        }
        
        // 如果上下文不是Map，生成一个默认的会话ID
        return "default-session-" + UUID.randomUUID().toString();
    }
    
    /**
     * 创建Agent工厂，用于创建带跟踪功能的Agent
     */
    @Component
    public static class TrackedAgentFactory {
        
        private final AgentExecutionTracker executionTracker;
        
        @Autowired
        public TrackedAgentFactory(AgentExecutionTracker executionTracker) {
            this.executionTracker = executionTracker;
        }
        
        /**
         * 创建带跟踪功能的Agent工具
         */
        public <B extends AbstractAgent.Builder<B>> Map.Entry<ToolSpecification, ToolExecutor> createTrackedAgent(AbstractAgent<B> agent) {
            Map.Entry<ToolSpecification, ToolExecutor> originalTool = agent.asTool();
            
            // 创建带跟踪功能的执行器
            TrackedAgentExecutor trackedExecutor = new TrackedAgentExecutor(
                executionTracker,
                originalTool.getValue(),
                agent.name(),
                agent.getClass().getSimpleName()
            );
            
            log.info("Created tracked agent: {} ({})", agent.name(), agent.getClass().getSimpleName());
            
            return Map.entry(originalTool.getKey(), trackedExecutor);
        }
        
        /**
         * 批量创建带跟踪功能的Agent工具
         */
        public <B extends AbstractAgent.Builder<B>> Map.Entry<ToolSpecification, ToolExecutor>[] createTrackedAgents(AbstractAgent<B>... agents) {
            @SuppressWarnings("unchecked")
            Map.Entry<ToolSpecification, ToolExecutor>[] trackedAgents = new Map.Entry[agents.length];
            
            for (int i = 0; i < agents.length; i++) {
                trackedAgents[i] = createTrackedAgent(agents[i]);
            }
            
            return trackedAgents;
        }
    }
    
    /**
     * 工具调用跟踪器
     * 用于跟踪Agent内部的工具调用
     */
    @Component
    public static class ToolCallTracker {
        
        private final AgentExecutionTracker executionTracker;
        
        @Autowired
        public ToolCallTracker(AgentExecutionTracker executionTracker) {
            this.executionTracker = executionTracker;
        }
        
        /**
         * 记录工具调用
         */
        public void recordToolCall(String sessionId, String agentName, String toolName, Object input, Object output) {
            executionTracker.recordToolCall(sessionId, agentName, toolName, input, output);
        }
        
        /**
         * 包装工具执行器，添加调用跟踪
         */
        public ToolExecutor wrapToolExecutor(ToolExecutor originalExecutor, String toolName, String agentName) {
            return new ToolExecutor() {
                @Override
                public String execute(ToolExecutionRequest request, Object context) {
                    String sessionId = extractSessionId(context);
                    
                    try {
                        String result = originalExecutor.execute(request, context);
                        recordToolCall(sessionId, agentName, toolName, request.arguments(), result);
                        return result;
                    } catch (Exception e) {
                        recordToolCall(sessionId, agentName, toolName, request.arguments(), "ERROR: " + e.getMessage());
                        throw e;
                    }
                }
            };
        }
        
        private String extractSessionId(Object context) {
            if (context instanceof Map) {
                Map<?, ?> contextMap = (Map<?, ?>) context;
                Object sessionId = contextMap.get("sessionId");
                if (sessionId != null) {
                    return sessionId.toString();
                }
            }
            return "default-session-" + UUID.randomUUID().toString();
        }
    }
}
