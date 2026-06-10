package com.openmanus.agentteam.application;

/**
 * Role-scoped execution entry for the agentteam module.
 */
public interface AgentTeamRoleExecutionPort {

    String executeSync(AgentTeamRole role, String input, String conversationId);

    default String executeSync(AgentTeamRole role, String input, String conversationId, String worktreePath) {
        return executeSync(role, input, conversationId);
    }
}
