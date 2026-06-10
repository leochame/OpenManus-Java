package com.openmanus.agentteam.infra;

import com.openmanus.agentteam.domain.port.GitIntegrationPort;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.List;

/**
 * Local Git integration adapter for creating branches and cherry-picking commits.
 */
@Slf4j
public class LocalGitIntegrationService implements GitIntegrationPort {

    private final GitCommandRunner commandRunner;

    public LocalGitIntegrationService() {
        this(new ProcessGitCommandRunner());
    }

    LocalGitIntegrationService(GitCommandRunner commandRunner) {
        this.commandRunner = commandRunner;
    }

    @Override
    public String createIntegrationBranch(Path repositoryPath, String branchName, String baseRef) {
        Path repo = normalize(repositoryPath);
        validate(branchName, "branchName");
        validate(baseRef, "baseRef");
        log.info("Git integration creating branch: repositoryPath={}, branch={}, baseRef={}", repo, branchName, baseRef);
        requireSuccess(repo, List.of("git", "checkout", "-b", branchName.trim(), baseRef.trim()),
                "failed to create integration branch " + branchName);
        log.info("Git integration branch ready: repositoryPath={}, branch={}", repo, branchName);
        return branchName.trim();
    }

    @Override
    public void cherryPickCommit(Path repositoryPath, String commitSha) {
        Path repo = normalize(repositoryPath);
        validate(commitSha, "commitSha");
        log.info("Git integration cherry-picking commit: repositoryPath={}, commitSha={}", repo, commitSha);
        requireSuccess(repo, List.of("git", "cherry-pick", commitSha.trim()),
                "failed to cherry-pick commit " + commitSha);
        log.info("Git integration cherry-pick completed: repositoryPath={}, commitSha={}", repo, commitSha);
    }

    private GitCommandResult requireSuccess(Path workingDirectory, List<String> command, String failureMessage) {
        GitCommandResult result = commandRunner.run(workingDirectory, command);
        if (!result.isSuccess()) {
            throw new GitWorktreeProvisioningException(failureMessage + ": " + firstNonBlankLine(result.stderr(), result.stdout()));
        }
        return result;
    }

    private Path normalize(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("repositoryPath must not be null");
        }
        return path.toAbsolutePath().normalize();
    }

    private void validate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private String firstNonBlankLine(String primary, String fallback) {
        if (primary != null) {
            for (String line : primary.split("\\R")) {
                if (!line.isBlank()) {
                    return line.trim();
                }
            }
        }
        if (fallback != null) {
            for (String line : fallback.split("\\R")) {
                if (!line.isBlank()) {
                    return line.trim();
                }
            }
        }
        return "no additional details";
    }
}
