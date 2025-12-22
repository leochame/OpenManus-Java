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
import java.util.UUID;
import java.util.concurrent.Executor;

import static com.openmanus.infra.log.LogMarkers.TO_FRONTEND;

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
            // 【重要日志】工作流开始执行
            log.info(TO_FRONTEND, "╔══════════════════════════════════════════════════════════════╗");
            log.info(TO_FRONTEND, "║  🚀 OPENMANUS AI 引擎启动                                     ║");
            log.info(TO_FRONTEND, "╠══════════════════════════════════════════════════════════════╣");
            log.info(TO_FRONTEND, "║  📋 任务接收成功，开始智能分析...                              ║");
            log.info(TO_FRONTEND, "║  🔗 会话ID: {}                              ", sessionId.substring(0, 8) + "...");
            log.info(TO_FRONTEND, "╚══════════════════════════════════════════════════════════════╝");
            
            executionTracker.startAgentExecution(sessionId, "workflow_manager", "WORKFLOW_START", userInput);
            String result = thinkDoReflectWorkflow.executeSync(userInput);

            // 记录结束事件
            executionTracker.endAgentExecution(sessionId, "workflow_manager", "WORKFLOW_COMPLETE", result, AgentExecutionEvent.ExecutionStatus.SUCCESS);

            // 计算执行时间
            LocalDateTime endTime = LocalDateTime.now();
            long executionTimeMs = ChronoUnit.MILLIS.between(startTime, endTime);

            // 【重要日志】工作流执行成功
            log.info(TO_FRONTEND, "╔══════════════════════════════════════════════════════════════╗");
            log.info(TO_FRONTEND, "║  ✅ 任务执行完成                                              ║");
            log.info(TO_FRONTEND, "╠══════════════════════════════════════════════════════════════╣");
            log.info(TO_FRONTEND, "║  ⏱️  总耗时: {}ms                                              ", executionTimeMs);
            log.info(TO_FRONTEND, "║  📊 状态: 成功                                                ║");
            log.info(TO_FRONTEND, "╚══════════════════════════════════════════════════════════════╝");

            // 发送结果到前端
            sendWorkflowResult(sessionId, userInput, result, "SUCCESS", endTime, executionTimeMs);

        } catch (Exception e) {
            // 【重要日志】工作流执行出错
            log.error(TO_FRONTEND, "╔══════════════════════════════════════════════════════════════╗");
            log.error(TO_FRONTEND, "║  ❌ 任务执行异常                                              ║");
            log.error(TO_FRONTEND, "╠══════════════════════════════════════════════════════════════╣");
            log.error(TO_FRONTEND, "║  ⚠️  错误信息: {}                                              ", e.getMessage());
            log.error(TO_FRONTEND, "╚══════════════════════════════════════════════════════════════╝");
            
            executionTracker.recordAgentError(sessionId, "workflow_manager", "WORKFLOW_EXECUTION", e.getMessage());

            // 发送错误结果
            long executionTimeMs = ChronoUnit.MILLIS.between(startTime, LocalDateTime.now());
            sendWorkflowResult(sessionId, userInput, "执行出错: " + e.getMessage(), "ERROR", LocalDateTime.now(), executionTimeMs);
            
        } finally {
            try {
                // 休眠以确保所有消息发送完成
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            log.debug("异步任务执行结束，正在清理监听器。");
            executionTracker.removeListener(listener);
        }
    }

    /**
     * 发送工作流结果到前端
     * 如果 WebSocket 会话已关闭，静默忽略错误
     */
    private void sendWorkflowResult(String sessionId, String userInput, String result, 
                                   String status, LocalDateTime completedTime, long executionTimeMs) {
        WorkflowResultVO resultVO = WorkflowResultVO.builder()
                .sessionId(sessionId)
                .userInput(userInput)
                .result(result)
                .status(status)
                .completedTime(completedTime)
                .executionTime(executionTimeMs)
                .build();

        String resultDestination = "/topic/executions/" + sessionId + "/result";
        try {
            log.debug("发送工作流结果到 {}", resultDestination);
            messagingTemplate.convertAndSend(resultDestination, resultVO);
        } catch (Exception e) {
            // 静默处理：WebSocket 会话可能已关闭，这是正常的竞态条件
            log.debug("无法发送结果到会话 {}: {}", sessionId, e.getMessage());
        }
    }
} 