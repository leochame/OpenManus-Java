package com.openmanus.agentteam.domain.model;

import java.util.List;

/**
 * A code-oriented multi-agent execution group.
 */
public record CodeTaskGroup(
        String groupId,
        String conversationId,
        String userGoal,
        List<CodeSubTask> subTasks
) {

    public CodeTaskGroup {
        subTasks = subTasks == null ? List.of() : List.copyOf(subTasks);
    }
}
