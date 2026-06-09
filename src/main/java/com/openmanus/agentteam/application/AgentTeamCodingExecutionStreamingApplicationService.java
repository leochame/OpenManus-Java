package com.openmanus.agentteam.application;

import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.model.ExecutionErrorCodes;
import com.openmanus.domain.model.ExecutionResponse;
import com.openmanus.domain.model.ExecutionResultView;
import com.openmanus.domain.service.ExecutionEventPort;
import com.openmanus.domain.service.ExecutionStreamPublisher;
import com.openmanus.domain.service.SessionExecutionGuard;
import com.openmanus.domain.service.SessionIdPolicy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Streaming application service for worktree-based multi-agent coding execution.
 */
@Slf4j
public class AgentTeamCodingExecutionStreamingApplicationService {

    private static final String SESSION_ID_KEY = "sessionId";
    private static final String EXECUTION_COORDINATOR = "agentteam_coding_coordinator";
    private static final String EXECUTION_START = "AGENTTEAM_CODING_EXECUTION_START";
    private static final String EXECUTION_COMPLETE = "AGENTTEAM_CODING_EXECUTION_COMPLETE";
    private static final String EXECUTION_ERROR = "AGENTTEAM_CODING_EXECUTION_ERROR";
    private static final String SESSION_BUSY_MESSAGE = "褰撳墠浼氳瘽姝ｅ湪鎵ц涓紝璇风◢鍚庨噸璇?";

    private final AgentTeamCodingApplicationService agentTeamCodingApplicationService;
    private final ExecutionEventPort executionEventPort;
    private final ExecutionStreamPublisher streamPublisher;
    private final Executor asyncExecutor;
    private final SessionExecutionGuard sessionExecutionGuard;
    private final Path repositoryPath;

    public AgentTeamCodingExecutionStreamingApplicationService(
            AgentTeamCodingApplicationService agentTeamCodingApplicationService,
            ExecutionEventPort executionEventPort,
            ExecutionStreamPublisher streamPublisher,
            Executor asyncExecutor,
            SessionExecutionGuard sessionExecutionGuard,
            Path repositoryPath
    ) {
        this.agentTeamCodingApplicationService = agentTeamCodingApplicationService;
        this.executionEventPort = executionEventPort;
        this.streamPublisher = streamPublisher;
        this.asyncExecutor = asyncExecutor;
        this.sessionExecutionGuard = sessionExecutionGuard;
        this.repositoryPath = repositoryPath;
    }

    public ExecutionResponse executeAndStreamEvents(
            String userInput,
            String requestedSessionId,
            String targetRepositoryPath
    ) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return ExecutionResponse.builder()
                    .success(false)
                    .error("杈撳叆涓嶈兘涓虹┖")
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
            log.error("Coding listener registration failed: sessionId={}", sessionId, exception);
            return ExecutionResponse.builder()
                    .success(false)
                    .sessionId(sessionId)
                    .executionId(executionId)
                    .error("鍐呴儴閿欒锛岃绋嶅悗閲嶈瘯")
                    .errorCode(ExecutionErrorCodes.INTERNAL_ERROR)
                    .build();
        }

        try {
            final String finalSessionId = sessionId;
            final String finalExecutionTopic = executionTopic;
            final String finalTargetRepositoryPath = targetRepositoryPath;
            asyncExecutor.execute(() -> executeExecutionInternal(
                    userInput,
                    finalSessionId,
                    finalExecutionTopic,
                    listener,
                    finalTargetRepositoryPath
            ));
        } catch (RejectedExecutionException exception) {
            removeListenerSafely(currentSessionId, listener);
            sessionExecutionGuard.release(sessionId);
            log.error("Coding async task rejected: sessionId={}", sessionId, exception);
            return ExecutionResponse.builder()
                    .success(false)
                    .sessionId(sessionId)
                    .executionId(executionId)
                    .error("浠诲姟鎻愪氦澶辫触锛岃绋嶅悗閲嶈瘯")
                    .errorCode(ExecutionErrorCodes.ASYNC_SUBMIT_REJECTED)
                    .build();
        } catch (RuntimeException exception) {
            removeListenerSafely(currentSessionId, listener);
            sessionExecutionGuard.release(sessionId);
            log.error("Coding async task submit failed: sessionId={}", sessionId, exception);
            return ExecutionResponse.builder()
                    .success(false)
                    .sessionId(sessionId)
                    .executionId(executionId)
                    .error("浠诲姟鎻愪氦寮傚父锛岃绋嶅悗閲嶈瘯")
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
            ExecutionEventPort.Listener listener,
            String targetRepositoryPath
    ) {
        LocalDateTime startTime = LocalDateTime.now();
        try (MDC.MDCCloseable ignored = MDC.putCloseable(SESSION_ID_KEY, sessionId)) {
            Path resolvedRepositoryPath = resolveRepositoryPath(targetRepositoryPath);
            log.info("AgentTeam coding execution started: sessionId={}, repositoryPath={}", sessionId, resolvedRepositoryPath);
            executionEventPort.startExecutionTracking(sessionId, userInput);
            executionEventPort.startExecution(sessionId, EXECUTION_COORDINATOR, EXECUTION_START, userInput);
            recordStageEvent(
                    sessionId,
                    "PLAN",
                    "Planning parallel coding subtasks",
                    Map.of("repositoryPath", resolvedRepositoryPath.toString())
            );

            var result = agentTeamCodingApplicationService.execute(
                    userInput,
                    sessionId,
                    targetRepositoryPath,
                    repositoryPath
            );
            recordStageEvent(sessionId, "SUMMARY", result.summary(), buildSummaryMetadata(result));
            if (result.integrationResult() != null) {
                recordStageEvent(
                        sessionId,
                        "INTEGRATION",
                        "integrationBranch=" + result.integrationResult().integrationBranch(),
                        buildIntegrationMetadata(result)
                );
            }

            String finalResult = result.summary();
            executionEventPort.endExecutionTracking(sessionId, finalResult, result.success());
            executionEventPort.endExecution(
                    sessionId,
                    EXECUTION_COORDINATOR,
                    EXECUTION_COMPLETE,
                    finalResult,
                    result.success() ? "SUCCESS" : "ERROR"
            );

            LocalDateTime endTime = LocalDateTime.now();
            long executionTimeMs = ChronoUnit.MILLIS.between(startTime, endTime);
            log.info("AgentTeam coding execution completed: sessionId={}, durationMs={}", sessionId, executionTimeMs);
            sendExecutionResult(
                    executionTopic,
                    sessionId,
                    userInput,
                    finalResult,
                    result.success() ? "SUCCESS" : "ERROR",
                    endTime,
                    executionTimeMs
            );
        } catch (RuntimeException exception) {
            Throwable actualError = unwrapException(exception);
            String errorMessage = safeErrorMessage(actualError);
            log.error("AgentTeam coding execution failed: sessionId={}", sessionId, exception);
            executionEventPort.endExecutionTracking(sessionId, "鎵ц鍑洪敊: " + errorMessage, false);
            executionEventPort.recordError(sessionId, EXECUTION_COORDINATOR, EXECUTION_ERROR, errorMessage);
            executionEventPort.endExecution(
                    sessionId,
                    EXECUTION_COORDINATOR,
                    EXECUTION_COMPLETE,
                    "鎵ц鍑洪敊: " + errorMessage,
                    "ERROR"
            );
            long executionTimeMs = ChronoUnit.MILLIS.between(startTime, LocalDateTime.now());
            sendExecutionResult(
                    executionTopic,
                    sessionId,
                    userInput,
                    "鎵ц鍑洪敊: " + errorMessage,
                    "ERROR",
                    LocalDateTime.now(),
                    executionTimeMs
            );
        } finally {
            removeListenerSafely(sessionId, listener);
            sessionExecutionGuard.release(sessionId);
        }
    }

    private void recordStageEvent(String sessionId, String stage, String detail) {
        recordStageEvent(sessionId, stage, detail, Map.of());
    }

    private void recordStageEvent(String sessionId, String stage, String detail, Map<String, Object> metadata) {
        executionEventPort.recordCustomEvent(AgentExecutionEvent.builder()
                .sessionId(sessionId)
                .eventId(UUID.randomUUID().toString())
                .agentName(EXECUTION_COORDINATOR)
                .agentType("agentteam_coding")
                .eventType(AgentExecutionEvent.EventType.INTERMEDIATE_RESULT)
                .status("RUNNING")
                .output(detail)
                .metadata(metadata.isEmpty() ? Map.of("stage", stage) : mergeStage(stage, metadata))
                .endTime(LocalDateTime.now())
                .build());
    }

    private Map<String, Object> mergeStage(String stage, Map<String, Object> metadata) {
        java.util.Map<String, Object> merged = new java.util.LinkedHashMap<>();
        merged.put("stage", stage);
        merged.putAll(metadata);
        return merged;
    }

    private Map<String, Object> buildSummaryMetadata(
            com.openmanus.agentteam.domain.model.ParallelCodingExecutionResult result
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("success", result.success());
        metadata.put("fallbackToSingleAgent", result.fallbackToSingleAgent());
        if (result.taskGroup() != null) {
            metadata.put("groupId", safe(result.taskGroup().groupId()));
            metadata.put("conversationId", safe(result.taskGroup().conversationId()));
            metadata.put("taskCount", result.taskGroup().subTasks().size());
            metadata.put("tasks", result.taskGroup().subTasks().stream()
                    .map(task -> Map.<String, Object>of(
                            "taskId", safe(task.taskId()),
                            "title", safe(task.title()),
                            "goal", safe(task.goal()),
                            "ownedPaths", task.ownedPaths(),
                            "verificationCommands", task.verificationCommands(),
                            "conflictRisk", safe(task.conflictRisk())
                    ))
                    .toList());
        }
        metadata.put("subAgents", result.subAgentResults().stream()
                .map(subAgent -> {
                    Map<String, Object> subAgentMap = new LinkedHashMap<>();
                    subAgentMap.put("taskId", safe(subAgent.taskId()));
                    subAgentMap.put("status", subAgent.status() == null ? "" : subAgent.status().name());
                    subAgentMap.put("summary", safe(subAgent.summary()));
                    subAgentMap.put("branchName", safe(subAgent.branchName()));
                    subAgentMap.put("commitSha", safe(subAgent.commitSha()));
                    subAgentMap.put("worktreePath", safe(subAgent.worktreePath()));
                    subAgentMap.put("changedFiles", subAgent.changedFiles());
                    subAgentMap.put("testPassed", subAgent.testPassed());
                    subAgentMap.put("testSummary", safe(subAgent.testSummary()));
                    subAgentMap.put("errorMessage", safe(subAgent.errorMessage()));
                    return subAgentMap;
                })
                .toList());
        return metadata;
    }

    private Map<String, Object> buildIntegrationMetadata(
            com.openmanus.agentteam.domain.model.ParallelCodingExecutionResult result
    ) {
        if (result.integrationResult() == null) {
            return Map.of();
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("integrationSuccess", result.integrationResult().success());
        metadata.put("integrationBranch", safe(result.integrationResult().integrationBranch()));
        metadata.put("mergedBranches", result.integrationResult().mergedBranches());
        metadata.put("verification", safe(result.integrationResult().testSummary()));
        metadata.put("conflictFiles", result.integrationResult().conflictFiles());
        metadata.put("errorMessage", safe(result.integrationResult().errorMessage()));
        metadata.put("subAgents", summarizeSubAgentsForIntegration(result.subAgentResults()));
        return metadata;
    }

    private List<Map<String, Object>> summarizeSubAgentsForIntegration(
            List<com.openmanus.agentteam.domain.model.SubAgentCodingResult> subAgentResults
    ) {
        return subAgentResults.stream()
                .map(subAgent -> Map.<String, Object>of(
                        "taskId", safe(subAgent.taskId()),
                        "branchName", safe(subAgent.branchName()),
                        "commitSha", safe(subAgent.commitSha()),
                        "status", subAgent.status() == null ? "" : subAgent.status().name()
                ))
                .toList();
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
            log.debug("Unable to publish coding result for session {}: {}", sessionId, exception.getMessage());
        }
    }

    private String resolveSessionId(String requestedSessionId) {
        String normalized = SessionIdPolicy.normalizeOrNull(requestedSessionId);
        if (normalized != null) {
            return normalized;
        }
        return UUID.randomUUID().toString();
    }

    private Path resolveRepositoryPath(String targetRepositoryPath) {
        if (targetRepositoryPath == null || targetRepositoryPath.isBlank()) {
            return repositoryPath;
        }
        return Path.of(targetRepositoryPath.trim()).toAbsolutePath().normalize();
    }

    private static String executionTopic(String sessionId, String executionId) {
        return "/topic/executions/" + sessionId + "/" + executionId;
    }

    private void removeListenerSafely(String sessionId, ExecutionEventPort.Listener listener) {
        try {
            executionEventPort.removeListener(sessionId, listener);
        } catch (RuntimeException exception) {
            log.warn("Coding listener cleanup failed: sessionId={}", sessionId, exception);
        }
    }

    private static Throwable unwrapException(Throwable throwable) {
        if (throwable == null) {
            return new IllegalStateException("unknown error");
        }
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String safeErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        return message.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
