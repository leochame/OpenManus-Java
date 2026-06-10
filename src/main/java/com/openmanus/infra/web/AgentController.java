package com.openmanus.infra.web;

import com.openmanus.agentteam.application.AgentTeamConversationApplicationService;
import com.openmanus.agentteam.application.AgentTeamCodingExecutionStreamingApplicationService;
import com.openmanus.agentteam.application.AgentTeamExecutionStreamingApplicationService;
import com.openmanus.domain.model.ExecutionErrorCodes;
import com.openmanus.domain.model.ExecutionRequest;
import com.openmanus.domain.model.ExecutionResponse;
import com.openmanus.domain.service.ConversationApplicationService;
import com.openmanus.domain.service.ExecutionStreamingApplicationService;
import com.openmanus.domain.service.SessionIdPolicy;
import com.openmanus.infra.config.AgentTeamProperties;
import com.openmanus.sandbox.domain.model.SessionSandboxInfo;
import com.openmanus.sandbox.application.SandboxSessionApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for interacting with Agent capabilities.
 * 提供统一执行链路的不同交互入口（HTTP 对话 / WebSocket 流式）。
 */
@RestController
@RequestMapping("/api/agent")
@Tag(name = "Agent API", description = "Web API interface for intelligent agent")
@Slf4j
public class AgentController {
    private static final String ERROR_EMPTY_INPUT = "输入不能为空";
    private static final String ERROR_SESSION_BUSY = "当前会话正在执行中，请稍后重试";
    private static final String ERROR_ASYNC_SUBMIT_FAILED = "任务提交失败，请稍后重试";
    private static final String ERROR_ASYNC_SUBMIT_EXCEPTION = "任务提交异常，请稍后重试";
    private final ConversationApplicationService conversationApplicationService;
    private final AgentTeamConversationApplicationService agentTeamConversationApplicationService;
    private final AgentTeamExecutionStreamingApplicationService agentTeamExecutionStreamingApplicationService;
    private final AgentTeamCodingExecutionStreamingApplicationService agentTeamCodingExecutionStreamingApplicationService;
    private final ExecutionStreamingApplicationService executionStreamingApplicationService;
    private final AgentTeamProperties agentTeamProperties;
    private final SandboxSessionApplicationService sandboxSessionApplicationService;

    public AgentController(
            ConversationApplicationService conversationApplicationService,
            AgentTeamConversationApplicationService agentTeamConversationApplicationService,
            AgentTeamExecutionStreamingApplicationService agentTeamExecutionStreamingApplicationService,
            AgentTeamCodingExecutionStreamingApplicationService agentTeamCodingExecutionStreamingApplicationService,
            ExecutionStreamingApplicationService executionStreamingApplicationService,
            AgentTeamProperties agentTeamProperties,
            SandboxSessionApplicationService sandboxSessionApplicationService) {
        this.conversationApplicationService = conversationApplicationService;
        this.agentTeamConversationApplicationService = agentTeamConversationApplicationService;
        this.agentTeamExecutionStreamingApplicationService = agentTeamExecutionStreamingApplicationService;
        this.agentTeamCodingExecutionStreamingApplicationService = agentTeamCodingExecutionStreamingApplicationService;
        this.executionStreamingApplicationService = executionStreamingApplicationService;
        this.agentTeamProperties = agentTeamProperties;
        this.sandboxSessionApplicationService = sandboxSessionApplicationService;
    }
    /**
     * 执行链路（HTTP 对话入口）
     *
     * @param payload 包含"message"和可选的"conversationId"
     * @param stateful 是否保持会话状态
     * @param sync 是否同步执行
     * @return Agent的响应
     */
    @PostMapping("/chat")
    @Operation(
        summary = "Agent Execution (HTTP Chat)",
        description = "Agent execution over HTTP. Supports both stateless and stateful conversation."
    )
    public CompletableFuture<ResponseEntity<Map<String, Object>>> chat(
            @RequestBody Map<String, String> payload,
            @RequestParam(defaultValue = "false") boolean stateful,
            @RequestParam(defaultValue = "false") boolean agentTeam,
            @RequestParam(defaultValue = "false") boolean sync) {

        String message = payload.get("message");
        String rawConversationId = payload.get("conversationId");
        String conversationId = stateful ? normalizeConversationId(rawConversationId) : null;
        if (message == null || message.trim().isEmpty()) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest()
                    .body(buildInputInvalidError("message不能为空", conversationId)));
        }

        if (stateful && payload.containsKey("conversationId") && conversationId == null) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest()
                    .body(buildInputInvalidError("conversationId非法", null)));
        }

        try {
            CompletableFuture<Map<String, Object>> execution = shouldUseAgentTeam(agentTeam)
                    ? agentTeamConversationApplicationService.chat(message, conversationId, sync)
                    : conversationApplicationService.chat(message, conversationId, sync);
            return execution.handle((result, throwable) -> throwable == null
                    ? toChatResponse(result)
                    : buildChatInternalErrorResponse(conversationId));
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(buildChatInternalErrorResponse(conversationId));
        }
    }

    /**
     * 执行链路（流式入口）
     *
     * @param executionRequest 包含用户输入的请求
     * @return 立即返回一个包含sessionId的响应，用于WebSocket订阅
     */
    @PostMapping({"/execution-stream", "/workflow-stream"})
    @Operation(
            summary = "Agent Execution (Streaming)",
            description = "Runs the same agent execution pipeline and returns a session ID for WebSocket streaming."
    )
    public ResponseEntity<ExecutionStreamResponse> executionStream(
            @RequestBody ExecutionRequest executionRequest,
            @RequestParam(defaultValue = "false") boolean agentTeam,
            @RequestParam(defaultValue = "false") boolean agentTeamCoding) {
        String userInput = executionRequest.getInput();
        log.info(
                "executionStream request received: sessionId={}, agentTeam={}, agentTeamCoding={}, targetRepositoryPath={}",
                executionRequest.getSessionId(),
                agentTeam,
                agentTeamCoding,
                executionRequest.getTargetRepositoryPath()
        );
        ExecutionResponse serviceResult;
        if (shouldUseAgentTeamCoding(agentTeamCoding)) {
            serviceResult = agentTeamCodingExecutionStreamingApplicationService.executeAndStreamEvents(
                    userInput,
                    executionRequest.getSessionId(),
                    executionRequest.getTargetRepositoryPath()
            );
        } else if (shouldUseAgentTeam(agentTeam)) {
            serviceResult = agentTeamExecutionStreamingApplicationService.executeAndStreamEvents(
                    userInput,
                    executionRequest.getSessionId()
            );
        } else {
            serviceResult = executionStreamingApplicationService.executeAndStreamEvents(
                    userInput,
                    executionRequest.getSessionId()
            );
        }

        if (!serviceResult.isSuccess()) {
            HttpStatus status = resolveErrorStatus(serviceResult.getErrorCode(), serviceResult.getError());
            return ResponseEntity.status(status).body(toStreamResponse(serviceResult));
        }

        // 从服务层获取sessionId
        String sessionId = serviceResult.getSessionId();
        if (sessionId == null) {
            // 如果没有sessionId，返回一个错误
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ExecutionStreamResponse.builder()
                    .success(false)
                    .error("无法启动执行链路：未生成会话ID")
                    .errorCode(ExecutionErrorCodes.INTERNAL_ERROR)
                    .build());
        }
        return ResponseEntity.ok(toStreamResponse(serviceResult));
    }

    private ExecutionStreamResponse toStreamResponse(ExecutionResponse serviceResult) {
        ExecutionStreamResponse.ExecutionStreamResponseBuilder builder = ExecutionStreamResponse.builder()
                .success(serviceResult.isSuccess())
                .sessionId(serviceResult.getSessionId())
                .executionId(serviceResult.getExecutionId())
                .error(serviceResult.getError())
                .errorCode(serviceResult.getErrorCode());
        if (serviceResult.isSuccess() && serviceResult.getSessionId() != null && serviceResult.getExecutionId() != null) {
            builder.topic(executionTopic(serviceResult.getSessionId(), serviceResult.getExecutionId()));
        }
        return builder.build();
    }

    private static String executionTopic(String sessionId, String executionId) {
        return "/topic/executions/" + sessionId + "/" + executionId;
    }

    private HttpStatus resolveErrorStatus(String errorCode, String error) {
        if (ExecutionErrorCodes.INPUT_INVALID.equals(errorCode)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ExecutionErrorCodes.SESSION_BUSY.equals(errorCode)) {
            return HttpStatus.CONFLICT;
        }
        if (ExecutionErrorCodes.AGENTTEAM_TASK_OWNERSHIP_VIOLATION.equals(errorCode)
                || ExecutionErrorCodes.AGENTTEAM_TASK_STATE_INVALID.equals(errorCode)) {
            return HttpStatus.CONFLICT;
        }
        if (ExecutionErrorCodes.ASYNC_SUBMIT_REJECTED.equals(errorCode)
                || ExecutionErrorCodes.ASYNC_SUBMIT_EXCEPTION.equals(errorCode)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (ExecutionErrorCodes.INTERNAL_ERROR.equals(errorCode)
                || ExecutionErrorCodes.AGENTTEAM_EXECUTION_FAILED.equals(errorCode)) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (ExecutionErrorCodes.WORKTREE_UNAVAILABLE.equals(errorCode)
                || ExecutionErrorCodes.PLAN_NOT_PARALLELIZABLE.equals(errorCode)) {
            return HttpStatus.BAD_REQUEST;
        }

        // Backward-compatible fallback for payloads without errorCode.
        if (ERROR_EMPTY_INPUT.equals(error)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ERROR_SESSION_BUSY.equals(error)) {
            return HttpStatus.CONFLICT;
        }
        if (ERROR_ASYNC_SUBMIT_FAILED.equals(error) || ERROR_ASYNC_SUBMIT_EXCEPTION.equals(error)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ResponseEntity<Map<String, Object>> toChatResponse(Map<String, Object> result) {
        if (result == null) {
            return buildChatInternalErrorResponse(null);
        }
        Object error = result.get("error");
        if (!(error instanceof String errorMessage) || errorMessage.isBlank()) {
            return ResponseEntity.ok(result);
        }

        Map<String, Object> response = new HashMap<>(result);
        String errorCode = stringValue(result.get("errorCode"));
        if (errorCode == null) {
            errorCode = inferChatErrorCode(errorMessage);
            response.put("errorCode", errorCode);
        }
        return ResponseEntity.status(resolveErrorStatus(errorCode, errorMessage)).body(response);
    }

    private ResponseEntity<Map<String, Object>> buildChatInternalErrorResponse(String conversationId) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "内部错误，请稍后重试");
        error.put("errorCode", ExecutionErrorCodes.INTERNAL_ERROR);
        if (conversationId != null && !conversationId.isBlank()) {
            error.put("conversationId", conversationId);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private Map<String, Object> buildInputInvalidError(String errorMessage, String conversationId) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", errorMessage);
        error.put("errorCode", ExecutionErrorCodes.INPUT_INVALID);
        if (conversationId != null && !conversationId.isBlank()) {
            error.put("conversationId", conversationId);
        }
        return error;
    }

    private String inferChatErrorCode(String errorMessage) {
        if ("message不能为空".equals(errorMessage)) {
            return ExecutionErrorCodes.INPUT_INVALID;
        }
        if (ERROR_SESSION_BUSY.equals(errorMessage)) {
            return ExecutionErrorCodes.SESSION_BUSY;
        }
        return ExecutionErrorCodes.INTERNAL_ERROR;
    }

    private String stringValue(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return null;
    }

    private static String normalizeConversationId(String rawConversationId) {
        return SessionIdPolicy.normalizeOrNull(rawConversationId);
    }

    private boolean shouldUseAgentTeam(boolean agentTeamRequested) {
        boolean result = agentTeamRequested && agentTeamProperties.isEnabled();
        if (agentTeamRequested) {
            log.info(
                    "AgentTeam routing decision: agentTeamRequested=true, agentTeamEnabled={}, path={}",
                    agentTeamProperties.isEnabled(),
                    result ? "AGENT_TEAM" : "SINGLE_AGENT (fallback)"
            );
        }
        return result;
    }

    private boolean shouldUseAgentTeamCoding(boolean agentTeamCodingRequested) {
        boolean result = agentTeamCodingRequested && agentTeamProperties.isEnabled();
        if (agentTeamCodingRequested) {
            log.info(
                    "AgentTeamCoding routing decision: agentTeamCodingRequested=true, agentTeamEnabled={}, path={}",
                    agentTeamProperties.isEnabled(),
                    result ? "AGENT_TEAM_CODING (worktree)" : "SINGLE_AGENT (fallback)"
            );
        }
        return result;
    }

    /**
     * 查询会话信息（包括沙箱状态）
     * 
     * @param sessionId 会话 ID
     * @return 会话信息，包含沙箱 VNC URL（如果已创建）
     */
    @GetMapping("/session/{sessionId}")
    @Operation(
        summary = "Get Session Info",
        description = "Returns session information including VNC sandbox URL if available. " +
                      "Frontend can use this to poll for sandbox status and display the browser workspace."
    )
    public ResponseEntity<Map<String, Object>> getSessionInfo(@PathVariable String sessionId) {
        String normalizedSessionId = normalizeConversationId(sessionId);
        if (normalizedSessionId == null) {
            return ResponseEntity.badRequest().body(buildSessionIdInvalidError());
        }

        return ResponseEntity.ok(buildSessionInfoResponse(
                normalizedSessionId,
                sandboxSessionApplicationService.getSandboxInfo(normalizedSessionId).orElse(null)
        ));
    }

    /**
     * 显式创建/启动会话沙箱。
     *
     * @param sessionId 会话 ID
     * @return 启动后的会话沙箱信息
     */
    @PostMapping("/session/{sessionId}/sandbox/start")
    @Operation(
        summary = "Start Session Sandbox",
        description = "Creates or reuses the session sandbox and returns its current status and VNC URL."
    )
    public ResponseEntity<Map<String, Object>> startSessionSandbox(@PathVariable String sessionId) {
        String normalizedSessionId = normalizeConversationId(sessionId);
        if (normalizedSessionId == null) {
            return ResponseEntity.badRequest().body(buildSessionIdInvalidError());
        }

        try {
            SessionSandboxInfo sandboxInfo = sandboxSessionApplicationService.getOrCreateSandbox(normalizedSessionId);
            return ResponseEntity.ok(buildSessionInfoResponse(normalizedSessionId, sandboxInfo));
        } catch (RuntimeException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("sessionId", normalizedSessionId);
            error.put("error", "沙箱启动失败: " + e.getMessage());
            error.put("errorCode", ExecutionErrorCodes.INTERNAL_ERROR);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
        }
    }

    private Map<String, Object> buildSessionIdInvalidError() {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "sessionId非法");
        error.put("errorCode", ExecutionErrorCodes.INPUT_INVALID);
        return error;
    }

    private Map<String, Object> buildSessionInfoResponse(String sessionId, SessionSandboxInfo sandboxInfo) {
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        if (sandboxInfo != null) {
            response.put("sandboxWorkspaceRoot", sandboxInfo.getWorkspaceRoot());
            response.put("sandboxVncUrl", sandboxInfo.getVncUrl());
            response.put("sandboxStatus", sandboxInfo.getStatus() == null ? "" : sandboxInfo.getStatus().toString());
            response.put("sandboxCreatedAt", sandboxInfo.getCreatedAt());
            response.put("sandboxAvailable", sandboxInfo.isAvailable());
        }
        return response;
    }
} 
