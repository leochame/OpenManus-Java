package com.openmanus.agentteam.application;

/**
 * Role-scoped execution entry for the agentteam module.
 */
public interface AgentTeamRoleExecutionPort {

    String executeSync(AgentTeamExecutionContext context, String input);
}
