package com.openmanus.agentteam.infra;

import com.openmanus.agentteam.domain.model.GitRepositoryRuntime;
import com.openmanus.agentteam.domain.model.GitWorktreeInfo;
import com.openmanus.agentteam.domain.port.GitWorktreeProvisioningPort;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Local Git-based worktree provisioning adapter.
 */
public class GitWorktreeProvisioningService implements GitWorktreeProvisioningPort {

    private final GitCommandRunner commandRunner;

    public GitWorktreeProvisioningService() {
        this(new ProcessGitCommandRunner());
    }

    GitWorktreeProvisioningService(GitCommandRunner commandRunner) {
        this.commandRunner = commandRunner;
    }

    @Override
    public GitRepositoryRuntime inspectRepository(Path repositoryPath) {
        Path normalizedRepositoryPath = normalizeRepositoryPath(repositoryPath);
        GitCommandResult version = safeRun(null, List.of("git", "--version"));
        if (!version.isSuccess()) {
            return new GitRepositoryRuntime(
                    false,
                    false,
                    blankToNull(version.stdout()),
                    null,
                    null,
                    null,
                    false,
                    "git command is not available: " + firstNonBlankLine(version.stderr(), version.stdout())
            );
        }

        GitCommandResult insideWorkTree = safeRun(normalizedRepositoryPath, List.of("git", "rev-parse", "--is-inside-work-tree"));
        if (!insideWorkTree.isSuccess() || !"true".equalsIgnoreCase(insideWorkTree.stdout().trim())) {
            return new GitRepositoryRuntime(
                    true,
                    false,
                    version.stdout().trim(),
                    null,
                    null,
                    null,
                    false,
                    "current path is not a git repository"
            );
        }

        GitCommandResult topLevel = requireSuccess(
                normalizedRepositoryPath,
                List.of("git", "rev-parse", "--show-toplevel"),
                "failed to resolve git repository root"
        );
        GitCommandResult branch = requireSuccess(
                normalizedRepositoryPath,
                List.of("git", "branch", "--show-current"),
                "failed to resolve current git branch"
        );
        GitCommandResult head = requireSuccess(
                normalizedRepositoryPath,
                List.of("git", "rev-parse", "HEAD"),
                "failed to resolve current git HEAD"
        );
        GitCommandResult status = requireSuccess(
                normalizedRepositoryPath,
                List.of("git", "status", "--short"),
                "failed to inspect git working tree status"
        );
        return new GitRepositoryRuntime(
                true,
                true,
                version.stdout().trim(),
                topLevel.stdout().trim(),
                blankToNull(branch.stdout()),
                blankToNull(head.stdout()),
                status.stdout().isBlank(),
                null
        );
    }

    @Override
    public List<GitWorktreeInfo> listWorktrees(Path repositoryPath) {
        Path normalizedRepositoryPath = normalizeRepositoryPath(repositoryPath);
        assertGitRepository(normalizedRepositoryPath);
        GitCommandResult result = requireSuccess(
                normalizedRepositoryPath,
                List.of("git", "worktree", "list", "--porcelain"),
                "failed to list git worktrees"
        );
        return parseWorktreeList(result.stdout());
    }

    @Override
    public GitWorktreeInfo createWorktree(Path repositoryPath, Path worktreePath, String branchName, String baseRef) {
        Path normalizedRepositoryPath = normalizeRepositoryPath(repositoryPath);
        assertGitRepository(normalizedRepositoryPath);
        validateRequired(branchName, "branchName");
        validateRequired(baseRef, "baseRef");
        Path normalizedWorktreePath = normalizeWorktreePath(worktreePath);
        ensureParentExists(normalizedWorktreePath);

        requireSuccess(
                normalizedRepositoryPath,
                List.of(
                        "git",
                        "worktree",
                        "add",
                        normalizedWorktreePath.toString(),
                        "-b",
                        branchName.trim(),
                        baseRef.trim()
                ),
                "failed to create git worktree for branch " + branchName
        );
        return listWorktrees(normalizedRepositoryPath).stream()
                .filter(worktree -> sameNormalizedPath(normalizedWorktreePath, worktree.path()))
                .findFirst()
                .orElseThrow(() -> new GitWorktreeProvisioningException(
                        "git worktree was created but not found in worktree list: " + normalizedWorktreePath
                ));
    }

    @Override
    public void removeWorktree(Path repositoryPath, Path worktreePath, boolean force) {
        Path normalizedRepositoryPath = normalizeRepositoryPath(repositoryPath);
        assertGitRepository(normalizedRepositoryPath);
        Path normalizedWorktreePath = normalizeWorktreePath(worktreePath);

        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("worktree");
        command.add("remove");
        command.add(normalizedWorktreePath.toString());
        if (force) {
            command.add("--force");
        }
        requireSuccess(
                normalizedRepositoryPath,
                command,
                "failed to remove git worktree " + normalizedWorktreePath
        );
    }

    private void assertGitRepository(Path repositoryPath) {
        GitRepositoryRuntime runtime = inspectRepository(repositoryPath);
        if (!runtime.supportsWorktreeOperations()) {
            throw new GitWorktreeProvisioningException(
                    runtime.failureReason() == null ? "git worktree mode is not available" : runtime.failureReason()
            );
        }
    }

    private GitCommandResult safeRun(Path workingDirectory, List<String> command) {
        try {
            return commandRunner.run(workingDirectory, command);
        } catch (GitWorktreeProvisioningException exception) {
            return new GitCommandResult(1, "", exception.getMessage());
        }
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

    private List<GitWorktreeInfo> parseWorktreeList(String stdout) {
        List<GitWorktreeInfo> worktrees = new ArrayList<>();
        String currentPath = null;
        String currentBranchRef = null;
        String currentHead = null;
        boolean detached = false;

        for (String line : stdout.split("\\R")) {
            if (line.isBlank()) {
                if (currentPath != null) {
                    worktrees.add(new GitWorktreeInfo(currentPath, currentBranchRef, currentHead, detached));
                }
                currentPath = null;
                currentBranchRef = null;
                currentHead = null;
                detached = false;
                continue;
            }
            if (line.startsWith("worktree ")) {
                currentPath = normalizeListedPath(line.substring("worktree ".length()).trim());
            } else if (line.startsWith("HEAD ")) {
                currentHead = line.substring("HEAD ".length()).trim();
            } else if (line.startsWith("branch ")) {
                currentBranchRef = line.substring("branch ".length()).trim();
            } else if ("detached".equals(line.trim())) {
                detached = true;
            }
        }
        if (currentPath != null) {
            worktrees.add(new GitWorktreeInfo(currentPath, currentBranchRef, currentHead, detached));
        }
        return worktrees;
    }

    private boolean sameNormalizedPath(Path normalizedPath, String listedPath) {
        if (listedPath == null || listedPath.isBlank()) {
            return false;
        }
        return normalizedPath.equals(Path.of(listedPath).toAbsolutePath().normalize());
    }

    private String normalizeListedPath(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        return Path.of(path).toAbsolutePath().normalize().toString();
    }

    private void ensureParentExists(Path worktreePath) {
        try {
            Path parent = worktreePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception exception) {
            throw new GitWorktreeProvisioningException(
                    "failed to prepare worktree parent directory: " + worktreePath,
                    exception
            );
        }
    }

    private Path normalizeRepositoryPath(Path repositoryPath) {
        if (repositoryPath == null) {
            throw new IllegalArgumentException("repositoryPath must not be null");
        }
        return repositoryPath.toAbsolutePath().normalize();
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
