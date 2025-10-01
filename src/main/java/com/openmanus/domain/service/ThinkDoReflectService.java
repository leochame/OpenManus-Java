package com.openmanus.domain.service;

import com.openmanus.agent.workflow.ThinkDoReflectWorkflow;
import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.model.WorkflowResponse;
import com.openmanus.domain.model.WorkflowResultVO;
import com.openmanus.infra.monitoring.AgentExecutionTracker;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 处理Think-Do-Reflect工作流相关的业务逻辑
 */
@Service
@Slf4j
public class ThinkDoReflectService {

    private final ThinkDoReflectWorkflow thinkDoReflectWorkflow;
    private final AgentExecutionTracker executionTracker;
    private final SimpMessagingTemplate messagingTemplate;
    private final Executor asyncExecutor; // 注入自定义线程池

    @Autowired
    public ThinkDoReflectService(ThinkDoReflectWorkflow thinkDoReflectWorkflow,
                                 AgentExecutionTracker executionTracker,
                                 SimpMessagingTemplate messagingTemplate,
                                 @Qualifier("asyncExecutor") Executor asyncExecutor) {
        this.thinkDoReflectWorkflow = thinkDoReflectWorkflow;
        this.executionTracker = executionTracker;
        this.messagingTemplate = messagingTemplate;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * 以流式方式执行Think-Do-Reflect工作流，并通过WebSocket发送事件
     *
     * @param userInput 用户输入
     * @return 包含sessionId的WorkflowResponse，用于客户端订阅
     */
    public WorkflowResponse executeWorkflowAndStreamEvents(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return WorkflowResponse.builder()
                    .success(false)
                    .error("输入不能为空")
                    .build();
        }

        String sessionId = MDC.get("sessionId");
        if (sessionId == null) {
            log.warn("MDC中未找到sessionId，将生成一个新的。请检查拦截器配置。");
            sessionId = UUID.randomUUID().toString();
            MDC.put("sessionId", sessionId);
        }

        String destination = "/topic/executions/" + sessionId;

        AgentExecutionTracker.AgentExecutionEventListener listener = event -> {
            log.debug("Sending event to {}: {}", destination, event);
            messagingTemplate.convertAndSend(destination, event);
        };
        executionTracker.addListener(listener);

        // 直接使用注入的Executor来异步执行任务
        final String finalSessionId = sessionId;
        asyncExecutor.execute(() -> {
            executeWorkflowInternal(userInput, finalSessionId, listener);
        });

        // 立即返回sessionId，以便客户端可以开始监听
        return WorkflowResponse.builder()
                .success(true)
                .sessionId(sessionId)
                .topic(destination)
                .build();
    }

    /**
     * 执行工作流的核心同步逻辑
     * @param userInput 用户输入
     * @param sessionId 会话ID
     * @param listener 事件监听器
     */
    public void executeWorkflowInternal(String userInput, String sessionId, AgentExecutionTracker.AgentExecutionEventListener listener) {
        // 由于TtlExecutor已在线程池配置中应用，MDC上下文会自动传递
        final LocalDateTime startTime = LocalDateTime.now();

        try (MDC.MDCCloseable ignored = MDC.putCloseable("sessionId", sessionId)) {
            log.info("异步任务开始执行，会话ID: {}", sessionId);
            executionTracker.startAgentExecution(sessionId, "workflow_manager", "WORKFLOW_START", userInput);
            String result = thinkDoReflectWorkflow.executeSync(userInput);

            // 记录结束事件
            executionTracker.endAgentExecution(sessionId, "workflow_manager", "WORKFLOW_COMPLETE", result, AgentExecutionEvent.ExecutionStatus.SUCCESS);

            // 计算执行时间
            LocalDateTime endTime = LocalDateTime.now();
            long executionTimeMs = ChronoUnit.MILLIS.between(startTime, endTime);

            // 创建并发送工作流结果VO
            WorkflowResultVO resultVO = WorkflowResultVO.builder()
                    .sessionId(sessionId)
                    .userInput(userInput)
                    .result(result)
                    .status("SUCCESS")
                    .completedTime(endTime)
                    .executionTime(executionTimeMs)
                    .build();

            // 发送到专门的结果主题
            String resultDestination = "/topic/executions/" + sessionId + "/result";
            log.info("发送工作流结果到 {}", resultDestination);
            messagingTemplate.convertAndSend(resultDestination, resultVO);

            log.info("工作流执行成功，执行时间: {}ms", executionTimeMs);

            // 额外休眠100毫秒确保消息被发送
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            log.error("工作流执行出错，会话ID: {}", sessionId, e);
            executionTracker.recordAgentError(sessionId, "workflow_manager", "WORKFLOW_EXECUTION", e.getMessage());

            // 发送错误结果
            WorkflowResultVO errorResult = WorkflowResultVO.builder()
                    .sessionId(sessionId)
                    .userInput(userInput)
                    .status("ERROR")
                    .result("执行出错: " + e.getMessage())
                    .completedTime(LocalDateTime.now())
                    .executionTime(ChronoUnit.MILLIS.between(startTime, LocalDateTime.now()))
                    .build();

            messagingTemplate.convertAndSend("/topic/executions/" + sessionId + "/result", errorResult);
        } finally {
            try {
                // 额外休眠以确保所有消息发送完成
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            log.info("异步任务执行结束，正在清理监听器。");
            executionTracker.removeListener(listener);
        }
    }

    /**
     * 执行Think-Do-Reflect工作流
     * (保留原始方法，但流式接口是主要入口)
     */
    public CompletableFuture<Map<String, Object>> executeWorkflow(String userInput, boolean sync) {
        if (userInput == null || userInput.trim().isEmpty()) {
            Map<String, Object> errorBody = Map.of(
                "success", false,
                "error", "输入不能为空"
            );
            return CompletableFuture.completedFuture(errorBody);
        }

        String sessionId = UUID.randomUUID().toString();
        
        executionTracker.startAgentExecution(sessionId, "workflow_manager", "WORKFLOW_START", userInput);

        if (sync) {
            try {
                String result = thinkDoReflectWorkflow.executeSync(userInput);
                
                executionTracker.endAgentExecution(sessionId, "workflow_manager", 
                    "WORKFLOW_COMPLETE", result, AgentExecutionEvent.ExecutionStatus.SUCCESS);
                
                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("success", true);
                responseBody.put("result", result);
                responseBody.put("input", userInput);
                responseBody.put("sessionId", sessionId);
                return CompletableFuture.completedFuture(responseBody);
            } catch (Exception e) {
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