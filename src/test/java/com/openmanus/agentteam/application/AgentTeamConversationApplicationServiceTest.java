package com.openmanus.agentteam.application;

import com.openmanus.domain.model.ExecutionErrorCodes;
import com.openmanus.domain.service.ExecutionEventPort;
import com.openmanus.domain.service.SessionExecutionGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AgentTeamConversationApplicationService Tests")
class AgentTeamConversationApplicationServiceTest {

    @Test
    @DisplayName("should expose ownership violation error code instead of unknown error")
    void shouldExposeOwnershipViolationErrorCodeInsteadOfUnknownError() {
        MasterAgentOrchestrator orchestrator = mock(MasterAgentOrchestrator.class);
        when(orchestrator.execute(anyString(), anyString()))
                .thenThrow(new TaskOwnershipViolationException("Task is not owned by agent: subagent-2"));

        ExecutionEventPort executionEventPort = mock(ExecutionEventPort.class);
        SessionExecutionGuard sessionExecutionGuard = mock(SessionExecutionGuard.class);
        when(sessionExecutionGuard.tryAcquire("conv-1")).thenReturn(true);
        Executor executor = Runnable::run;

        AgentTeamConversationApplicationService service = new AgentTeamConversationApplicationService(
                new AgentTeamApplicationService(orchestrator),
                executionEventPort,
                sessionExecutionGuard,
                executor
        );

        Map<String, Object> result = service.chat("hello", "conv-1", true).join();

        assertThat(result).containsEntry("errorCode", ExecutionErrorCodes.AGENTTEAM_TASK_OWNERSHIP_VIOLATION);
        assertThat(String.valueOf(result.get("error"))).contains("Task is not owned by agent");
        verify(sessionExecutionGuard).release("conv-1");
    }
}
