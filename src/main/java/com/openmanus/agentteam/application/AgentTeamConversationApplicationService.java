package com.openmanus.agentteam.application;

import com.openmanus.domain.model.ExecutionErrorCodes;
import com.openmanus.domain.service.ExecutionEventPort;
import com.openmanus.domain.service.SessionExecutionGuard;
import com.openmanus.domain.service.SessionIdPolicy;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * HTTP-facing application service for the agentteam execution path.
 */
public class AgentTeamConversationApplicationService {

    private static final String EXECUTION_COORDINATOR = "agentteam_coordinator";
    private static final String EXECUTION_START = "AGENTTEAM_EXECUTION_START";
    private static final String EXECUTION_COMPLETE = "AGENTTEAM_EXECUTION_COMPLETE";
    private static final String EXECUTION_ERROR = "AGENTTEAM_EXECUTION_ERROR";
    private static final String DEFAULT_USER_ID = "001";
    private static final String SESSION_BUSY_MESSAGE = "当前会话正在执行中，请稍后重试";

    private final AgentTeamApplicationService agentTeamApplicationService;
    private final ExecutionEventPort executionEventPort;
    private final SessionExecutionGuard sessionExecutionGuard;
    private final Executor asyncExecutor;

    public AgentTeamConversationApplicationService(
            AgentTeamApplicationService agentTeamApplicationService,
            ExecutionEventPort executionEventPort,
            SessionExecutionGuard sessionExecutionGuard,
            Executor asyncExecutor
    ) {
        this.agentTeamApplicationService = agentTeamApplicationService;
        this.executionEventPort = executionEventPort;
        this.sessionExecutionGuard = sessionExecutionGuard;
        this.asyncExecutor = asyncExecutor;
    }

    public CompletableFuture<Map<String, Object>> chat(String message, String conversationId, boolean sync) {
        final String sessionId = normalizeSessionId(conversationId);
        final String userId = currentUserId();

        if (message == null || message.trim().isEmpty()) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "message不能为空");
            errorResult.put("errorCode", ExecutionErrorCodes.INPUT_INVALID);
            errorResult.put("conversationId", sessionId);
            return CompletableFuture.completedFuture(errorResult);
        }

        if (!sessionExecutionGuard.tryAcquire(sessionId)) {
            return CompletableFuture.completedFuture(busyResult(sessionId));
        }

        if (sync) {
            try {
                return CompletableFuture.completedFuture(executeSyncWithMonitoring(message, sessionId, userId));
            } finally {
                sessionExecutionGuard.release(sessionId);
            }
        }

        return CompletableFuture.supplyAsync(() -> executeSyncWithMonitoring(message, sessionId, userId), asyncExecutor)
                .exceptionally(error -> completeFailure(sessionId, AgentTeamErrorSupport.unwrap(error)))
                .whenComplete((ignored, throwable) -> sessionExecutionGuard.release(sessionId));
    }

    private Map<String, Object> executeSyncWithMonitoring(String message, String sessionId, String userId) {
        try (MDC.MDCCloseable ignoredSession = MDC.putCloseable("sessionId", sessionId);
             MDC.MDCCloseable ignoredUser = MDC.putCloseable("userId", userId)) {
            startMonitoring(sessionId, message);
            String response = agentTeamApplicationService.execute(message, sessionId);
            return completeSuccess(sessionId, response);
        } catch (Exception error) {
            return completeFailure(sessionId, error);
        }
    }

    private void startMonitoring(String sessionId, String message) {
        executionEventPort.startExecutionTracking(sessionId, message);
        executionEventPort.startExecution(sessionId, EXECUTION_COORDINATOR, EXECUTION_START, message);
    }

    private Map<String, Object> completeSuccess(String sessionId, String response) {
        executionEventPort.endExecutionTracking(sessionId, response, true);
        executionEventPort.endExecution(
                sessionId,
                EXECUTION_COORDINATOR,
                EXECUTION_COMPLETE,
                response,
                "SUCCESS"
        );
        return successResult(sessionId, response);
    }

    private Map<String, Object> completeFailure(String sessionId, Throwable error) {
        String message = AgentTeamErrorSupport.safeMessage(error);
        String errorCode = AgentTeamErrorSupport.errorCode(error);
        executionEventPort.endExecutionTracking(sessionId, "执行出错: " + message, false);
        executionEventPort.recordError(sessionId, EXECUTION_COORDINATOR, EXECUTION_ERROR, message);
        executionEventPort.endExecution(
                sessionId,
                EXECUTION_COORDINATOR,
                EXECUTION_COMPLETE,
                "执行出错: " + message,
                "ERROR"
        );
        return errorResult(sessionId, message, errorCode);
    }

    private static Map<String, Object> successResult(String sessionId, String response) {
        Map<String, Object> result = new HashMap<>();
        result.put("answer", response);
        result.put("conversationId", sessionId);
        result.put("timestamp", LocalDateTime.now().toString());
        result.put("mode", "agentteam");
        return result;
    }

    private static Map<String, Object> errorResult(String sessionId, String message, String errorCode) {
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("error", message);
        errorResult.put("errorCode", errorCode);
        errorResult.put("conversationId", sessionId);
        return errorResult;
    }

    private static Map<String, Object> busyResult(String sessionId) {
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("error", SESSION_BUSY_MESSAGE);
        errorResult.put("errorCode", ExecutionErrorCodes.SESSION_BUSY);
        errorResult.put("conversationId", sessionId);
        return errorResult;
    }

    private static String normalizeSessionId(String rawConversationId) {
        return SessionIdPolicy.normalizeOrGenerate(rawConversationId);
    }

    private static String currentUserId() {
        String userId = MDC.get("userId");
        return userId == null || userId.isBlank() ? DEFAULT_USER_ID : userId;
    }
}
