package com.openmanus.agentteam.domain.port;

import com.openmanus.agentteam.domain.model.GitRepositoryRuntime;
import com.openmanus.agentteam.domain.model.GitWorktreeInfo;

import java.nio.file.Path;
import java.util.List;

/**
 * Port for local Git worktree lifecycle management.
 */
public interface GitWorktreeProvisioningPort {

    GitRepositoryRuntime inspectRepository(Path repositoryPath);

    List<GitWorktreeInfo> listWorktrees(Path repositoryPath);

    GitWorktreeInfo createWorktree(Path repositoryPath, Path worktreePath, String branchName, String baseRef);

    void removeWorktree(Path repositoryPath, Path worktreePath, boolean force);
}
