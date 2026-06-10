package com.openmanus.agentteam.infra;

import com.openmanus.agentteam.domain.model.GitWorkspaceSnapshot;
import com.openmanus.agentteam.domain.port.GitWorkspacePort;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Inspects and commits changes inside one local Git worktree.
 */
@Slf4j
public class LocalGitWorkspaceService implements GitWorkspacePort {

    private static final String DEFAULT_COMMIT_USER_NAME = "OpenManus AgentTeam";
    private static final String DEFAULT_COMMIT_USER_EMAIL = "agentteam@openmanus.local";

    private final GitCommandRunner commandRunner;

    public LocalGitWorkspaceService() {
        this(new ProcessGitCommandRunner());
    }

    LocalGitWorkspaceService(GitCommandRunner commandRunner) {
        this.commandRunner = commandRunner;
    }

    @Override
    public GitWorkspaceSnapshot inspectWorkspace(Path worktreePath) {
        Path normalizedWorktreePath = normalizeWorktreePath(worktreePath);
        GitCommandResult branchResult = requireSuccess(
                normalizedWorktreePath,
                List.of("git", "branch", "--show-current"),
                "failed to resolve worktree branch"
        );
        GitCommandResult headResult = requireSuccess(
                normalizedWorktreePath,
                List.of("git", "rev-parse", "HEAD"),
                "failed to resolve worktree HEAD"
        );
        GitCommandResult statusResult = requireSuccess(
                normalizedWorktreePath,
                List.of("git", "status", "--short"),
                "failed to inspect worktree status"
        );

        List<String> changedFiles = parseChangedFiles(statusResult.stdout());
        GitWorkspaceSnapshot snapshot = new GitWorkspaceSnapshot(
                blankToNull(branchResult.stdout()),
                blankToNull(headResult.stdout()),
                changedFiles.isEmpty(),
                changedFiles
        );
        log.info(
                "Git workspace inspected: branch={}, worktreePath={}, clean={}, changedFiles={}",
                snapshot.branchName(),
                normalizedWorktreePath,
                snapshot.clean(),
                snapshot.changedFiles()
        );
        return snapshot;
    }

    @Override
    public String commitAllChanges(Path worktreePath, String commitMessage) {
        Path normalizedWorktreePath = normalizeWorktreePath(worktreePath);
        validateRequired(commitMessage, "commitMessage");
        GitWorkspaceSnapshot beforeCommit = inspectWorkspace(normalizedWorktreePath);
        if (beforeCommit.clean()) {
            log.info(
                    "Git workspace commit skipped because no changes were detected: branch={}, worktreePath={}",
                    beforeCommit.branchName(),
                    normalizedWorktreePath
            );
            return beforeCommit.headCommit();
        }

        log.info(
                "Git workspace committing changes: branch={}, worktreePath={}, changedFiles={}, commitMessage={}",
                beforeCommit.branchName(),
                normalizedWorktreePath,
                beforeCommit.changedFiles(),
                commitMessage
        );
        requireSuccess(
                normalizedWorktreePath,
                List.of("git", "add", "-A"),
                "failed to stage worktree changes"
        );
        requireSuccess(
                normalizedWorktreePath,
                List.of(
                        "git",
                        "-c",
                        "user.name=" + DEFAULT_COMMIT_USER_NAME,
                        "-c",
                        "user.email=" + DEFAULT_COMMIT_USER_EMAIL,
                        "commit",
                        "-m",
                        commitMessage.trim()
                ),
                "failed to commit worktree changes"
        );
        GitWorkspaceSnapshot afterCommit = inspectWorkspace(normalizedWorktreePath);
        log.info(
                "Git workspace commit completed: branch={}, worktreePath={}, commitSha={}",
                afterCommit.branchName(),
                normalizedWorktreePath,
                afterCommit.headCommit()
        );
        return afterCommit.headCommit();
    }

    private GitCommandResult requireSuccess(Path workingDirectory, List<String> command, String failureMessage) {
        GitCommandResult result = commandRunner.run(workingDirectory, command);
        if (!result.isSuccess()) {
            throw new GitWorktreeProvisioningException(
                    failureMessage + ": " + firstNonBlankLine(result.stderr(), result.stdout())
            );
        }
        return result;
    }

    private List<String> parseChangedFiles(String stdout) {
        List<String> changedFiles = new ArrayList<>();
        for (String line : stdout.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.length() <= 3) {
                continue;
            }
            changedFiles.add(trimmed.substring(3).trim());
        }
        return changedFiles;
    }

    private Path normalizeWorktreePath(Path worktreePath) {
        if (worktreePath == null) {
            throw new IllegalArgumentException("worktreePath must not be null");
        }
        return worktreePath.toAbsolutePath().normalize();
    }

    private void validateRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlankLine(String primary, String fallback) {
        String candidate = pickFirstNonBlankLine(primary);
        if (candidate != null) {
            return candidate;
        }
        String fallbackLine = pickFirstNonBlankLine(fallback);
        return fallbackLine == null ? "no additional details" : fallbackLine;
    }

    private String pickFirstNonBlankLine(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (String line : value.split("\\R")) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return null;
    }
}
