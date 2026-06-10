package com.openmanus.agentteam.domain.model;

import java.util.List;

/**
 * Snapshot of Git change state for one worktree.
 */
public record GitWorkspaceSnapshot(
        String branchName,
        String headCommit,
        boolean clean,
        List<String> changedFiles
) {

    public GitWorkspaceSnapshot {
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
    }
}
