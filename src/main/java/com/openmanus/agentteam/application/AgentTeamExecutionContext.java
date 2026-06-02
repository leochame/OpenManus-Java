package com.openmanus.agentteam.application;

/**
 * Stable execution identity for one agent-team runtime call.
 */
public record AgentTeamExecutionContext(
        AgentTeamRole role,
        String parentSessionId,
        String groupId,
        String taskId,
        String agentId,
        int depth,
        String memoryId
) {

    public AgentTeamExecutionContext {
        role = role == null ? AgentTeamRole.SUB_AGENT : role;
        parentSessionId = normalize(parentSessionId);
        groupId = normalize(groupId);
        taskId = normalize(taskId);
        agentId = normalize(agentId);
        depth = Math.max(0, depth);
        memoryId = normalize(memoryId);
    }

    public static AgentTeamExecutionContext subAgent(
            String parentSessionId,
            String groupId,
            String taskId,
            String agentId
    ) {
        return new AgentTeamExecutionContext(
                AgentTeamRole.SUB_AGENT,
                parentSessionId,
                groupId,
                taskId,
                agentId,
                1,
                AgentTeamMemoryIds.subAgent(parentSessionId, groupId, taskId, agentId)
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
