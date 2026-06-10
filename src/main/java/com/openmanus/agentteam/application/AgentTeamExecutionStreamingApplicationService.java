package com.openmanus.agentteam.application;

import com.openmanus.domain.model.ExecutionErrorCodes;
import com.openmanus.domain.model.ExecutionResponse;
import com.openmanus.domain.model.ExecutionResultView;
import com.openmanus.domain.service.ExecutionEventPort;
import com.openmanus.domain.service.ExecutionStreamPublisher;
import com.openmanus.domain.service.SessionExecutionGuard;
import com.openmanus.domain.service.SessionIdPolicy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Streaming application service for the agentteam execution path.
 */
@Slf4j
public class AgentTeamExecutionStreamingApplicationService {

    private static final String SESSION_ID_KEY = "sessionId";
    private static final String EXECUTION_COORDINATOR = "agentteam_coordinator";
    private static final String EXECUTION_START = "AGENTTEAM_EXECUTION_START";
    private static final String EXECUTION_COMPLETE = "AGENTTEAM_EXECUTION_COMPLETE";
    private static final String EXECUTION_ERROR = "AGENTTEAM_EXECUTION_ERROR";
    private static final String SESSION_BUSY_MESSAGE = "当前会话正在执行中，请稍后重试";

    private final AgentTeamApplicationService agentTeamApplicationService;
    private final ExecutionEventPort executionEventPort;
    private final ExecutionStreamPublisher streamPublisher;
    private final Executor asyncExecutor;
    private final SessionExecutionGuard sessionExecutionGuard;

    public AgentTeamExecutionStreamingApplicationService(
            AgentTeamApplicationService agentTeamApplicationService,
            ExecutionEventPort executionEventPort,
            ExecutionStreamPublisher streamPublisher,
            Executor asyncExecutor,
            SessionExecutionGuard sessionExecutionGuard
    ) {
        this.agentTeamApplicationService = agentTeamApplicationService;
        this.executionEventPort = executionEventPort;
        this.streamPublisher = streamPublisher;
        this.asyncExecutor = asyncExecutor;
        this.sessionExecutionGuard = sessionExecutionGuard;
    }

    public ExecutionResponse executeAndStreamEvents(String userInput, String requestedSessionId) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return ExecutionResponse.builder()
                    .success(false)
                    .error("输入不能为空")
                    .errorCode(ExecutionErrorCodes.INPUT_INVALID)
                    .build();
        }

        String sessionId = resolveSessionId(requestedSessionId);
        if (!sessionExecutionGuard.tryAcquire(sessionId)) {
            return ExecutionResponse.builder()
                    .success(false)
                    .sessionId(sessionId)
                    .error(SESSION_BUSY_MESSAGE)
                    .errorCode(ExecutionErrorCodes.SESSION_BUSY)
                    .build();
        }
        String executionId = UUID.randomUUID().toString();
        String executionTopic = executionTopic(sessionId, executionId);

        final String currentSessionId = sessionId;
        ExecutionEventPort.Listener listener = event -> {
            if (event == null || !currentSessionId.equals(event.getSessionId())) {
                return;
            }
            streamPublisher.publishEvent(executionTopic, event);
        };

        try {
            executionEventPort.addListener(currentSessionId, listener);
        } catch (RuntimeException exception) {
            sessionExecutionGuard.release(sessionId);
            log.error("Listener registration failed: sessionId={}", sessionId, exception);
            return ExecutionResponse.builder()
                    .success(false)
                    .sessionId(sessionId)
                    .executionId(executionId)
                    .error("内部错误，请稍后重试")
                    .errorCode(ExecutionErrorCodes.INTERNAL_ERROR)
                    .build();
        }

        try {
            final String finalSessionId = sessionId;
            final String finalExecutionTopic = executionTopic;
            asyncExecutor.execute(() -> executeExecutionInternal(userInput, finalSessionId, finalExecutionTopic, listener));
        } catch (RejectedExecutionException exception) {
            removeListenerSafely(currentSessionId, listener);
            sessionExecutionGuard.release(sessionId);
            log.error("Async task rejected: sessionId={}", sessionId, exception);
            return ExecutionResponse.builder()
                    .success(false)
                    .sessionId(sessionId)
                    .executionId(executionId)
                    .error("任务提交失败，请稍后重试")
                    .errorCode(ExecutionErrorCodes.ASYNC_SUBMIT_REJECTED)
                    .build();
        } catch (RuntimeException exception) {
            removeListenerSafely(currentSessionId, listener);
            sessionExecutionGuard.release(sessionId);
            log.error("Async task submit failed: sessionId={}", sessionId, exception);
            return ExecutionResponse.builder()
                    .success(false)
                    .sessionId(sessionId)
                    .executionId(executionId)
                    .error("任务提交异常，请稍后重试")
                    .errorCode(ExecutionErrorCodes.ASYNC_SUBMIT_EXCEPTION)
                    .build();
        }

        return ExecutionResponse.builder()
                .success(true)
                .sessionId(sessionId)
                .executionId(executionId)
                .build();
    }

    void executeExecutionInternal(
            String userInput,
            String sessionId,
            String executionTopic,
            ExecutionEventPort.Listener listener
    ) {
        LocalDateTime startTime = LocalDateTime.now();
        try (MDC.MDCCloseable ignored = MDC.putCloseable(SESSION_ID_KEY, sessionId)) {
            log.info("AgentTeam execution started: sessionId={}", sessionId);
            executionEventPort.startExecutionTracking(sessionId, userInput);
            executionEventPort.startExecution(sessionId, EXECUTION_COORDINATOR, EXECUTION_START, userInput);
            String result = agentTeamApplicationService.execute(userInput, sessionId);
            executionEventPort.endExecutionTracking(sessionId, result, true);
            executionEventPort.endExecution(sessionId, EXECUTION_COORDINATOR, EXECUTION_COMPLETE, result, "SUCCESS");

            LocalDateTime endTime = LocalDateTime.now();
            long executionTimeMs = ChronoUnit.MILLIS.between(startTime, endTime);
            log.info("AgentTeam execution completed: sessionId={}, durationMs={}", sessionId, executionTimeMs);
            sendExecutionResult(executionTopic, sessionId, userInput, result, "SUCCESS", endTime, executionTimeMs);
        } catch (RuntimeException exception) {
            Throwable actualError = AgentTeamErrorSupport.unwrap(exception);
            String errorMessage = AgentTeamErrorSupport.safeMessage(actualError);
            String errorCode = AgentTeamErrorSupport.errorCode(actualError);
            log.error(
                    "AgentTeam execution failed: sessionId={}, errorCode={}, errorType={}, error={}",
                    sessionId,
                    errorCode,
                    AgentTeamErrorSupport.errorType(actualError),
                    errorMessage,
                    exception
            );
            executionEventPort.endExecutionTracking(sessionId, "执行出错: " + errorMessage, false);
            executionEventPort.recordError(sessionId, EXECUTION_COORDINATOR, EXECUTION_ERROR, errorMessage);
            executionEventPort.endExecution(
                    sessionId,
                    EXECUTION_COORDINATOR,
                    EXECUTION_COMPLETE,
                    "执行出错: " + errorMessage,
                    "ERROR"
            );
            long executionTimeMs = ChronoUnit.MILLIS.between(startTime, LocalDateTime.now());
            sendExecutionResult(
                    executionTopic,
                    sessionId,
                    userInput,
                    "执行出错: " + errorMessage,
                    "ERROR",
                    LocalDateTime.now(),
                    executionTimeMs
            );
        } finally {
            removeListenerSafely(sessionId, listener);
            sessionExecutionGuard.release(sessionId);
        }
    }

    private void sendExecutionResult(
            String executionTopic,
            String sessionId,
            String userInput,
            String result,
            String status,
            LocalDateTime completedTime,
            long executionTimeMs
    ) {
        ExecutionResultView resultView = ExecutionResultView.builder()
                .sessionId(sessionId)
                .userInput(userInput)
                .result(result)
                .status(status)
                .completedTime(completedTime)
                .executionTime(executionTimeMs)
                .build();
        try {
            streamPublisher.publishResult(executionTopic, resultView);
        } catch (Exception exception) {
            log.debug("Unable to publish result for session {}: {}", sessionId, exception.getMessage());
        }
    }

    private String resolveSessionId(String requestedSessionId) {
        String normalized = SessionIdPolicy.normalizeOrNull(requestedSessionId);
        if (normalized != null) {
            return normalized;
        }
        return UUID.randomUUID().toString();
    }

    private static String executionTopic(String sessionId, String executionId) {
        return "/topic/executions/" + sessionId + "/" + executionId;
    }

    private void removeListenerSafely(String sessionId, ExecutionEventPort.Listener listener) {
        try {
            executionEventPort.removeListener(sessionId, listener);
        } catch (RuntimeException exception) {
            log.warn("Listener cleanup failed: sessionId={}", sessionId, exception);
        }
    }

}
