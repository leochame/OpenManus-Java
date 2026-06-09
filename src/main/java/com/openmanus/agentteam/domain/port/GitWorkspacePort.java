package com.openmanus.agentteam.domain.port;

import com.openmanus.agentteam.domain.model.GitWorkspaceSnapshot;

import java.nio.file.Path;

/**
 * Port for inspecting and committing changes inside one Git worktree.
 */
public interface GitWorkspacePort {

    GitWorkspaceSnapshot inspectWorkspace(Path worktreePath);

    String commitAllChanges(Path worktreePath, String commitMessage);
}
