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
        String memoryId,
        String worktreePath
) {

    public AgentTeamExecutionContext {
        role = role == null ? AgentTeamRole.SUB_AGENT : role;
        parentSessionId = normalize(parentSessionId);
        groupId = normalize(groupId);
        taskId = normalize(taskId);
        agentId = normalize(agentId);
        depth = Math.max(0, depth);
        memoryId = normalize(memoryId);
        worktreePath = normalize(worktreePath);
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
                AgentTeamMemoryIds.subAgent(parentSessionId, groupId, taskId, agentId),
                ""
        );
    }

    public static AgentTeamExecutionContext codingSubAgent(
            String memoryId,
            String worktreePath
    ) {
        return new AgentTeamExecutionContext(
                AgentTeamRole.CODING_SUB_AGENT,
                "",
                "",
                "",
                "",
                0,
                memoryId,
                worktreePath
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
