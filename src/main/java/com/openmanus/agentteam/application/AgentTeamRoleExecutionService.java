package com.openmanus.agentteam.application;

import com.openmanus.agent.coordination.AgentCoordinator;
import com.openmanus.agentteam.infra.AgentTeamCoordinatorFactory;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Executes one request with the coordinator built for a specific agentteam role.
 */
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
                ? new AgentTeamExecutionContext(AgentTeamRole.SUB_AGENT, "", "", "", "", 0, "")
                : context;
        Object runtimeMemoryId = executionContext.memoryId() != null && !executionContext.memoryId().isBlank()
                ? executionContext.memoryId()
                : UUID.randomUUID();
        try (MDC.MDCCloseable ignoredSession = MDC.putCloseable("sessionId", String.valueOf(runtimeMemoryId));
             SafeCloseable ignoredGroup = putCloseable("agentTeamGroupId", executionContext.groupId());
             SafeCloseable ignoredTask = putCloseable("agentTeamTaskId", executionContext.taskId());
             SafeCloseable ignoredAgent = putCloseable("agentTeamAgentId", executionContext.agentId());
             SafeCloseable ignoredParent = putCloseable("agentTeamParentSessionId", executionContext.parentSessionId())) {
            AgentCoordinator coordinator = coordinatorFactory.create(executionContext.role());
            return coordinator.execute(input, runtimeMemoryId);
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
