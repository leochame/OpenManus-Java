package com.openmanus.agentteam.application;

import com.openmanus.agent.coordination.AgentCoordinator;
import com.openmanus.agentteam.infra.AgentTeamCoordinatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Executes one request with the coordinator built for a specific agentteam role.
 */
@Slf4j
public class AgentTeamRoleExecutionService implements AgentTeamRoleExecutionPort {

    @FunctionalInterface
    private interface SafeCloseable extends AutoCloseable {
        @Override
        void close();
    }

    private final AgentTeamCoordinatorFactory coordinatorFactory;

    public AgentTeamRoleExecutionService(AgentTeamCoordinatorFactory coordinatorFactory) {
        this.coordinatorFactory = coordinatorFactory;
    }

    @Override
    public String executeSync(AgentTeamExecutionContext context, String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input cannot be null or blank");
        }
        AgentTeamExecutionContext executionContext = context == null
                ? new AgentTeamExecutionContext(AgentTeamRole.SUB_AGENT, "", "", "", "", 0, "", "")
                : context;
        Object runtimeMemoryId = executionContext.memoryId() != null && !executionContext.memoryId().isBlank()
                ? executionContext.memoryId()
                : UUID.randomUUID();
        try (MDC.MDCCloseable ignoredSession = MDC.putCloseable("sessionId", String.valueOf(runtimeMemoryId));
             MDC.MDCCloseable ignoredUser = MDC.putCloseable("userId", String.valueOf(runtimeMemoryId));
             SafeCloseable ignoredGroup = putCloseable("agentTeamGroupId", executionContext.groupId());
             SafeCloseable ignoredTask = putCloseable("agentTeamTaskId", executionContext.taskId());
             SafeCloseable ignoredAgent = putCloseable("agentTeamAgentId", executionContext.agentId());
             SafeCloseable ignoredParent = putCloseable("agentTeamParentSessionId", executionContext.parentSessionId());
             SafeCloseable ignoredWorktree = putCloseable("worktreePath", executionContext.worktreePath())) {
            AgentCoordinator coordinator = coordinatorFactory.create(executionContext.role());
            log.info(
                    "AgentTeamRoleExecution starting: role={}, conversationId={}, worktreePath={}",
                    executionContext.role(),
                    runtimeMemoryId,
                    executionContext.worktreePath()
            );
            String response = coordinator.execute(input, runtimeMemoryId);
            log.info(
                    "AgentTeamRoleExecution completed: role={}, conversationId={}, worktreePath={}",
                    executionContext.role(),
                    runtimeMemoryId,
                    executionContext.worktreePath()
            );
            return response;
        }
    }

    private SafeCloseable putCloseable(String key, String value) {
        if (value == null || value.isBlank()) {
            return () -> { };
        }
        MDC.MDCCloseable closeable = MDC.putCloseable(key, value);
        return closeable::close;
    }
}
