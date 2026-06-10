package com.openmanus.agentteam.domain.port;

import java.nio.file.Path;

/**
 * Port for creating integration branches and applying subtask commits.
 */
public interface GitIntegrationPort {

    String createIntegrationBranch(Path repositoryPath, String branchName, String baseRef);

    void cherryPickCommit(Path repositoryPath, String commitSha);
}
