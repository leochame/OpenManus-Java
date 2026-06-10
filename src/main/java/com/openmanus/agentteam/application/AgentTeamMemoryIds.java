package com.openmanus.agentteam.application;

/**
 * Stable memory-id factory for agent-team scoped runtimes.
 */
public final class AgentTeamMemoryIds {

    private static final String PREFIX = "agentteam";
    private static final String BLANK_SEGMENT = "_";

    private AgentTeamMemoryIds() {
    }

    public static String subAgent(String parentSessionId, String groupId, String taskId, String agentId) {
        return String.join(":",
                PREFIX,
                segment(parentSessionId),
                segment(groupId),
                segment(taskId),
                segment(agentId));
    }

    private static String segment(String value) {
        if (value == null || value.isBlank()) {
            return BLANK_SEGMENT;
        }
        return value.trim();
    }
}
