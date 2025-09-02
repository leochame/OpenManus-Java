package com.openmanus.domain.service;

import com.openmanus.agent.workflow.ThinkDoReflectWorkflow;
import com.openmanus.infra.monitoring.AgentExecutionTracker;
import com.openmanus.domain.model.AgentExecutionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 处理Think-Do-Reflect工作流相关的业务逻辑
 */
@Service
@Slf4j
public class ThinkDoReflectService {

    private final ThinkDoReflectWorkflow thinkDoReflectWorkflow;
    private final AgentExecutionTracker executionTracker;

    @Autowired
    public ThinkDoReflectService(ThinkDoReflectWorkflow thinkDoReflectWorkflow, 
                                AgentExecutionTracker executionTracker) {
        this.thinkDoReflectWorkflow = thinkDoReflectWorkflow;
        this.executionTracker = executionTracker;
    }

    /**
     * 执行Think-Do-Reflect工作流
     * 
     * @param userInput 用户输入
     * @param sync 是否同步执行
     * @return 执行结果
     */
    public CompletableFuture<Map<String, Object>> executeWorkflow(String userInput, boolean sync) {
        if (userInput == null || userInput.trim().isEmpty()) {
            Map<String, Object> errorBody = Map.of(
                "success", false,
                "error", "输入不能为空"
            );
            return CompletableFuture.completedFuture(errorBody);
        }

        // 生成会话ID用于跟踪
        String sessionId = UUID.randomUUID().toString();
        
        // 记录工作流开始
        executionTracker.startAgentExecution(sessionId, "workflow_manager", "WORKFLOW_START", userInput);

        if (sync) {
            try {
                String result = thinkDoReflectWorkflow.executeSync(userInput);
                
                // 记录工作流完成
                executionTracker.endAgentExecution(sessionId, "workflow_manager", 
                    "WORKFLOW_COMPLETE", result, AgentExecutionEvent.ExecutionStatus.SUCCESS);
                
                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("success", true);
                responseBody.put("result", result);
                responseBody.put("input", userInput);
                responseBody.put("sessionId", sessionId);
                return CompletableFuture.completedFuture(responseBody);
            } catch (Exception e) {
                // 记录工作流错误
                executionTracker.recordAgentError(sessionId, "workflow_manager", 
                    "WORKFLOW_EXECUTION", e.getMessage());
                
                Map<String, Object> errorBody = new HashMap<>();
                errorBody.put("success", false);
                errorBody.put("error", e.getMessage());
                errorBody.put("input", userInput);
                errorBody.put("sessionId", sessionId);
                return CompletableFuture.completedFuture(errorBody);
            }
        } else {
            return thinkDoReflectWorkflow.execute(userInput)
                .thenApply(result -> {
                    // 记录工作流完成
                    executionTracker.endAgentExecution(sessionId, "workflow_manager", 
                        "WORKFLOW_COMPLETE", result, AgentExecutionEvent.ExecutionStatus.SUCCESS);
                    
                    Map<String, Object> responseBody = new HashMap<>();
                    responseBody.put("success", true);
                    responseBody.put("result", result);
                    responseBody.put("input", userInput);
                    responseBody.put("sessionId", sessionId);
                    return responseBody;
                })
                .exceptionally(throwable -> {
                    // 记录工作流错误
                    executionTracker.recordAgentError(sessionId, "workflow_manager", 
                        "WORKFLOW_EXECUTION", throwable.getMessage());
                    
                    Map<String, Object> errorBody = new HashMap<>();
                    errorBody.put("success", false);
                    errorBody.put("error", throwable.getMessage());
                    errorBody.put("input", userInput);
                    errorBody.put("sessionId", sessionId);
                    return errorBody;
                });
        }
    }
} 