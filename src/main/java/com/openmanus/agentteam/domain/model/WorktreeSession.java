package com.openmanus.agentteam.domain.model;

/**
 * Metadata describing one isolated Git worktree execution session.
 */
public record WorktreeSession(
        String sessionId,
        String branchName,
        String baseRef,
        String worktreePath
) {
}
