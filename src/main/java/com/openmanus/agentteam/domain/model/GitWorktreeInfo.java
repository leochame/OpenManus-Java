package com.openmanus.agentteam.domain.model;

/**
 * Git worktree metadata parsed from local Git state.
 */
public record GitWorktreeInfo(
        String path,
        String branchRef,
        String headCommit,
        boolean detached
) {
}
