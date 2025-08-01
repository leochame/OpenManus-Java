package com.openmanus.domain.controller;

import com.openmanus.agent.workflow.ThinkDoReflectWorkflow;
import com.openmanus.infra.monitoring.AgentExecutionTracker;
import com.openmanus.domain.model.AgentExecutionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Think-Do-Reflect系统的REST API控制器
 * 
 * 提供HTTP接口来测试和使用Think-Do-Reflect工作流。
 * 集成了AgentExecutionTracker以支持实时可视化。
 */
@RestController
@RequestMapping("/api/think-do-reflect")
@CrossOrigin(origins = "*")
public class ThinkDoReflectController {

    private final ThinkDoReflectWorkflow workflow;
    
    @Autowired
    private AgentExecutionTracker executionTracker;

    public ThinkDoReflectController(ThinkDoReflectWorkflow workflow) {
        this.workflow = workflow;
    }

    /**
     * 执行Think-Do-Reflect工作流（异步版本）
     * 支持实时可视化跟踪
     *
     * @param request 包含用户输入的请求
     * @return 异步执行结果
     */
    @PostMapping("/execute")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> execute(
            @RequestBody Map<String, String> request) {

        String userInput = request.get("input");
        if (userInput == null || userInput.trim().isEmpty()) {
            Map<String, Object> errorBody = Map.of("error", "输入不能为空");
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(errorBody)
            );
        }

        // 生成会话ID用于跟踪
        String sessionId = UUID.randomUUID().toString();
        
        // 记录工作流开始
        executionTracker.startAgentExecution(sessionId, "workflow_manager", "WORKFLOW_START", 
            userInput);

        return workflow.execute(userInput)
                .thenApply(result -> {
                    // 记录工作流完成
                    executionTracker.endAgentExecution(sessionId, "workflow_manager", 
                        "WORKFLOW_COMPLETE", result, AgentExecutionEvent.ExecutionStatus.SUCCESS);
                    
                    Map<String, Object> responseBody = new HashMap<>();
                    responseBody.put("success", true);
                    responseBody.put("result", result);
                    responseBody.put("input", userInput);
                    responseBody.put("sessionId", sessionId);
                    return ResponseEntity.ok(responseBody);
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
                    return ResponseEntity.internalServerError().body(errorBody);
                });
    }
    
    /**
     * 执行Think-Do-Reflect工作流（同步版本，方便调试）
     * 支持实时可视化跟踪
     *
     * @param request 包含用户输入的请求
     * @return 同步执行结果
     */
    @PostMapping("/execute-sync")
    public ResponseEntity<Map<String, Object>> executeSync(
            @RequestBody Map<String, String> request) {

        String userInput = request.get("input");
        if (userInput == null || userInput.trim().isEmpty()) {
            Map<String, Object> errorBody = Map.of("error", "输入不能为空");
            return ResponseEntity.badRequest().body(errorBody);
        }

        // 生成会话ID用于跟踪
        String sessionId = UUID.randomUUID().toString();
        
        // 记录工作流开始
        executionTracker.startAgentExecution(sessionId, "workflow_manager", "WORKFLOW_START", 
            userInput);

        try {
            String result = workflow.executeSync(userInput);
            
            // 记录工作流完成
            executionTracker.endAgentExecution(sessionId, "workflow_manager", 
                "WORKFLOW_COMPLETE", result, AgentExecutionEvent.ExecutionStatus.SUCCESS);
            
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", true);
            responseBody.put("result", result);
            responseBody.put("input", userInput);
            responseBody.put("sessionId", sessionId);
            return ResponseEntity.ok(responseBody);
        } catch (Exception e) {
            // 记录工作流错误
            executionTracker.recordAgentError(sessionId, "workflow_manager", 
                "WORKFLOW_EXECUTION", e.getMessage());
            
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("success", false);
            errorBody.put("error", e.getMessage());
            errorBody.put("input", userInput);
            errorBody.put("sessionId", sessionId);
            return ResponseEntity.internalServerError().body(errorBody);
        }
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> healthStatus = Map.of(
            "status", "healthy",
            "service", "Think-Do-Reflect Workflow",
            "timestamp", System.currentTimeMillis()
        );
        return ResponseEntity.ok(healthStatus);
    }
}
