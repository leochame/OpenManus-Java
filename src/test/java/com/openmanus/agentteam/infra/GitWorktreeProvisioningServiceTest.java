package com.openmanus.agentteam.infra;

import com.openmanus.agentteam.domain.model.GitRepositoryRuntime;
import com.openmanus.agentteam.domain.model.GitWorktreeInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GitWorktreeProvisioningService Tests")
class GitWorktreeProvisioningServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should report unavailable git when git command cannot start")
    void shouldReportUnavailableGitWhenGitCommandCannotStart() {
        GitCommandRunner failingRunner = (workingDirectory, command) -> {
            throw new GitWorktreeProvisioningException("git executable not found");
        };
        GitWorktreeProvisioningService service = new GitWorktreeProvisioningService(failingRunner);

        GitRepositoryRuntime runtime = service.inspectRepository(tempDir);

        assertThat(runtime.gitAvailable()).isFalse();
        assertThat(runtime.gitRepository()).isFalse();
        assertThat(runtime.failureReason()).contains("git command is not available");
    }

    @Test
    @DisplayName("should report non git repository for plain directory")
    void shouldReportNonGitRepositoryForPlainDirectory() {
        GitWorktreeProvisioningService service = new GitWorktreeProvisioningService();

        GitRepositoryRuntime runtime = service.inspectRepository(tempDir);

        assertThat(runtime.gitAvailable()).isTrue();
        assertThat(runtime.gitRepository()).isFalse();
        assertThat(runtime.failureReason()).isEqualTo("current path is not a git repository");
    }

    @Test
    @DisplayName("should create list and remove worktree for local repository")
    void shouldCreateListAndRemoveWorktreeForLocalRepository() throws Exception {
        GitWorktreeProvisioningService service = new GitWorktreeProvisioningService();
        Path repositoryPath = tempDir.resolve("repo");
        Files.createDirectories(repositoryPath);
        runGit(repositoryPath, "git", "init", "-b", "main");
        runGit(repositoryPath, "git", "config", "user.name", "OpenManus Test");
        runGit(repositoryPath, "git", "config", "user.email", "openmanus@example.com");
        Files.writeString(repositoryPath.resolve("README.md"), "hello");
        runGit(repositoryPath, "git", "add", "README.md");
        runGit(repositoryPath, "git", "commit", "-m", "initial commit");

        GitRepositoryRuntime runtime = service.inspectRepository(repositoryPath);
        assertThat(runtime.supportsWorktreeOperations()).isTrue();
        assertThat(runtime.currentBranch()).isEqualTo("main");
        assertThat(runtime.workingTreeClean()).isTrue();

        Path worktreePath = repositoryPath.resolve(".agentteam/worktrees/task-a");
        GitWorktreeInfo created = service.createWorktree(repositoryPath, worktreePath, "agentteam/task-a", "HEAD");

        assertThat(created.path()).isEqualTo(worktreePath.toAbsolutePath().normalize().toString());
        assertThat(created.branchRef()).isEqualTo("refs/heads/agentteam/task-a");

        List<GitWorktreeInfo> worktrees = service.listWorktrees(repositoryPath);
        assertThat(worktrees).extracting(GitWorktreeInfo::path)
                .contains(worktreePath.toAbsolutePath().normalize().toString());

        service.removeWorktree(repositoryPath, worktreePath, true);

        assertThat(Files.exists(worktreePath)).isFalse();
        assertThat(service.listWorktrees(repositoryPath)).extracting(GitWorktreeInfo::path)
                .doesNotContain(worktreePath.toAbsolutePath().normalize().toString());
    }

    @Test
    @DisplayName("should fail to create worktree when repository is not available")
    void shouldFailToCreateWorktreeWhenRepositoryIsNotAvailable() {
        GitWorktreeProvisioningService service = new GitWorktreeProvisioningService();

        assertThatThrownBy(() -> service.createWorktree(
                tempDir,
                tempDir.resolve("wt"),
                "agentteam/task-a",
                "HEAD"
        ))
                .isInstanceOf(GitWorktreeProvisioningException.class)
                .hasMessageContaining("current path is not a git repository");
    }

    @Test
    @DisplayName("should match created worktree when git list uses normalized slash path")
    void shouldMatchCreatedWorktreeWhenGitListUsesNormalizedSlashPath() {
        Path repositoryPath = tempDir.resolve("repo").toAbsolutePath().normalize();
        Path worktreePath = repositoryPath.resolve(".agentteam").resolve("worktrees").resolve("task-a")
                .toAbsolutePath()
                .normalize();
        String listedPath = worktreePath.toString().replace('\\', '/');

        GitCommandRunner runner = new GitCommandRunner() {
            @Override
            public GitCommandResult run(Path workingDirectory, List<String> command) {
                if (command.equals(List.of("git", "--version"))) {
                    return new GitCommandResult(0, "git version 2.47.0", "");
                }
                if (command.equals(List.of("git", "rev-parse", "--is-inside-work-tree"))) {
                    return new GitCommandResult(0, "true", "");
                }
                if (command.equals(List.of("git", "rev-parse", "--show-toplevel"))) {
                    return new GitCommandResult(0, repositoryPath.toString(), "");
                }
                if (command.equals(List.of("git", "branch", "--show-current"))) {
                    return new GitCommandResult(0, "main", "");
                }
                if (command.equals(List.of("git", "rev-parse", "HEAD"))) {
                    return new GitCommandResult(0, "head-1", "");
                }
                if (command.equals(List.of("git", "status", "--short"))) {
                    return new GitCommandResult(0, "", "");
                }
                if (command.equals(List.of("git", "worktree", "list", "--porcelain"))) {
                    return new GitCommandResult(
                            0,
                            "worktree " + repositoryPath.toString().replace('\\', '/') + System.lineSeparator()
                                    + "HEAD head-1" + System.lineSeparator()
                                    + "branch refs/heads/main" + System.lineSeparator()
                                    + System.lineSeparator()
                                    + "worktree " + listedPath + System.lineSeparator()
                                    + "HEAD head-1" + System.lineSeparator()
                                    + "branch refs/heads/agentteam/task-a" + System.lineSeparator(),
                            ""
                    );
                }
                if (command.size() >= 6
                        && "git".equals(command.get(0))
                        && "worktree".equals(command.get(1))
                        && "add".equals(command.get(2))) {
                    return new GitCommandResult(0, "Preparing worktree", "");
                }
                throw new IllegalStateException("unexpected command: " + command);
            }
        };

        GitWorktreeProvisioningService service = new GitWorktreeProvisioningService(runner);

        GitWorktreeInfo created = service.createWorktree(repositoryPath, worktreePath, "agentteam/task-a", "HEAD");

        assertThat(created.path()).isEqualTo(worktreePath.toString());
        assertThat(created.branchRef()).isEqualTo("refs/heads/agentteam/task-a");
    }

    private void runGit(Path workingDirectory, String... command) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory.toFile());
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        String stderr = new String(process.getErrorStream().readAllBytes());
        assertThat(exitCode).withFailMessage(stderr).isEqualTo(0);
    }
}
