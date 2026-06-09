package com.openmanus.agentteam.domain.model;

/**
 * Snapshot of local Git runtime and repository state for worktree orchestration.
 */
public record GitRepositoryRuntime(
        boolean gitAvailable,
        boolean gitRepository,
        String gitVersion,
        String repositoryRoot,
        String currentBranch,
        String headCommit,
        boolean workingTreeClean,
        String failureReason
) {

    public boolean supportsWorktreeOperations() {
        return gitAvailable && gitRepository && failureReason == null;
    }
}
