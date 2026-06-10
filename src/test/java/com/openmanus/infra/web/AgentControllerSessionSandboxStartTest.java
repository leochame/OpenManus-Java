package com.openmanus.infra.web;

import com.openmanus.agentteam.application.AgentTeamConversationApplicationService;
import com.openmanus.agentteam.application.AgentTeamCodingExecutionStreamingApplicationService;
import com.openmanus.agentteam.application.AgentTeamExecutionStreamingApplicationService;
import com.openmanus.domain.model.ExecutionErrorCodes;
import com.openmanus.domain.model.ExecutionResponse;
import com.openmanus.domain.service.ConversationApplicationService;
import com.openmanus.domain.service.ExecutionStreamingApplicationService;
import com.openmanus.infra.config.AgentTeamProperties;
import com.openmanus.sandbox.application.SandboxSessionApplicationService;
import com.openmanus.sandbox.domain.model.SessionSandboxInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AgentController Session Sandbox Start Tests")
class AgentControllerSessionSandboxStartTest {

    private final ConversationApplicationService conversationApplicationService =
            mock(ConversationApplicationService.class);
    private final AgentTeamConversationApplicationService agentTeamConversationApplicationService =
            mock(AgentTeamConversationApplicationService.class);
    private final AgentTeamExecutionStreamingApplicationService agentTeamExecutionStreamingApplicationService =
            mock(AgentTeamExecutionStreamingApplicationService.class);
    private final AgentTeamCodingExecutionStreamingApplicationService agentTeamCodingExecutionStreamingApplicationService =
            mock(AgentTeamCodingExecutionStreamingApplicationService.class);
    private final ExecutionStreamingApplicationService executionStreamingApplicationService =
            mock(ExecutionStreamingApplicationService.class);
    private final AgentTeamProperties agentTeamProperties = new AgentTeamProperties();
    private final SandboxSessionApplicationService sandboxSessionApplicationService =
            mock(SandboxSessionApplicationService.class);

    private final AgentController controller = new AgentController(
            conversationApplicationService,
            agentTeamConversationApplicationService,
            agentTeamExecutionStreamingApplicationService,
            agentTeamCodingExecutionStreamingApplicationService,
            executionStreamingApplicationService,
            agentTeamProperties,
            sandboxSessionApplicationService
    );

    @Test
    @DisplayName("startSessionSandbox should create sandbox and return session payload")
    void startSessionSandbox_createsSandbox() {
        String sessionId = "session-123";
        SessionSandboxInfo sandboxInfo = SessionSandboxInfo.builder()
                .sessionId(sessionId)
                .workspaceRoot("/workspace")
                .createdAt(LocalDateTime.of(2026, 4, 28, 20, 30))
                .status(SessionSandboxInfo.SandboxStatus.RUNNING)
                .build();
        when(sandboxSessionApplicationService.getOrCreateSandbox(sessionId)).thenReturn(sandboxInfo);

        ResponseEntity<Map<String, Object>> response = controller.startSessionSandbox(sessionId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsEntry("sessionId", sessionId);
        assertThat(response.getBody()).containsEntry("sandboxWorkspaceRoot", "/workspace");
        assertThat(response.getBody()).containsEntry("sandboxStatus", "RUNNING");
        assertThat(response.getBody()).containsEntry("sandboxAvailable", true);
    }

    @Test
    @DisplayName("startSessionSandbox should reject invalid sessionId")
    void startSessionSandbox_rejectsInvalidSessionId() {
        ResponseEntity<Map<String, Object>> response = controller.startSessionSandbox("../bad");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsEntry("errorCode", ExecutionErrorCodes.INPUT_INVALID);
    }

    @Test
    @DisplayName("startSessionSandbox should surface startup failure")
    void startSessionSandbox_returnsServiceUnavailableWhenSandboxFails() {
        String sessionId = "session-123";
        when(sandboxSessionApplicationService.getOrCreateSandbox(sessionId))
                .thenThrow(new RuntimeException("Docker 会话沙箱启动失败"));

        ResponseEntity<Map<String, Object>> response = controller.startSessionSandbox(sessionId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsEntry("sessionId", sessionId);
        assertThat(String.valueOf(response.getBody().get("error"))).contains("沙箱启动失败");
        assertThat(response.getBody()).containsEntry("errorCode", ExecutionErrorCodes.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("executionStream should map session busy to conflict")
    void executionStream_mapsSessionBusyToConflict() {
        when(executionStreamingApplicationService.executeAndStreamEvents("hello", "session-123"))
                .thenReturn(ExecutionResponse.builder()
                        .success(false)
                        .sessionId("session-123")
                        .error("当前会话正在执行中，请稍后重试")
                        .errorCode(ExecutionErrorCodes.SESSION_BUSY)
                        .build());

        var request = new com.openmanus.domain.model.ExecutionRequest();
        request.setInput("hello");
        request.setSessionId("session-123");

        ResponseEntity<ExecutionStreamResponse> response = controller.executionStream(request, false, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo(ExecutionErrorCodes.SESSION_BUSY);
    }
}
