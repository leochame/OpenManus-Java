package com.openmanus.agentteam.application;

import com.openmanus.agentteam.domain.model.CodeSubTask;
import com.openmanus.agentteam.domain.model.WorktreeSession;

/**
 * Application-layer request for one worktree-scoped coding execution.
 */
public record SubAgentCodingExecutionRequest(
        CodeSubTask subTask,
        WorktreeSession worktreeSession
) {
}
