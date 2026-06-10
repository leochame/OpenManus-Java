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

    private final AgentTeamCoordinatorFactory coordinatorFactory;

    public AgentTeamRoleExecutionService(AgentTeamCoordinatorFactory coordinatorFactory) {
        this.coordinatorFactory = coordinatorFactory;
    }

    @Override
    public String executeSync(AgentTeamRole role, String input, String conversationId) {
        return executeSync(role, input, conversationId, null);
    }

    @Override
    public String executeSync(AgentTeamRole role, String input, String conversationId, String worktreePath) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input cannot be null or blank");
        }
        Object memoryId = conversationId != null && !conversationId.isBlank()
                ? conversationId
                : UUID.randomUUID();
        if (worktreePath == null || worktreePath.isBlank()) {
            try (MDC.MDCCloseable ignoredSession = MDC.putCloseable("sessionId", String.valueOf(memoryId));
                 MDC.MDCCloseable ignoredUser = MDC.putCloseable("userId", String.valueOf(memoryId))) {
                return executeWithCoordinator(role, input, memoryId, null);
            }
        }
        try (MDC.MDCCloseable ignoredSession = MDC.putCloseable("sessionId", String.valueOf(memoryId));
             MDC.MDCCloseable ignoredUser = MDC.putCloseable("userId", String.valueOf(memoryId));
             MDC.MDCCloseable ignoredWorktree = MDC.putCloseable("worktreePath", worktreePath)) {
            return executeWithCoordinator(role, input, memoryId, worktreePath);
        }
    }

    private String executeWithCoordinator(AgentTeamRole role, String input, Object memoryId, String worktreePath) {
            log.info(
                    "AgentTeamRoleExecution starting: role={}, conversationId={}, worktreePath={}",
                    role,
                    memoryId,
                    worktreePath
            );
            AgentCoordinator coordinator = coordinatorFactory.create(role);
            String response = coordinator.execute(input, memoryId);
            log.info(
                    "AgentTeamRoleExecution completed: role={}, conversationId={}, worktreePath={}",
                    role,
                    memoryId,
                    worktreePath
            );
            return response;
    }
}
