package com.openmanus.agentteam.infra;

import com.openmanus.agentteam.domain.model.GitWorkspaceSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalGitWorkspaceService Tests")
class LocalGitWorkspaceServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should inspect clean workspace and commit pending changes")
    void shouldInspectCleanWorkspaceAndCommitPendingChanges() throws Exception {
        LocalGitWorkspaceService service = new LocalGitWorkspaceService();
        Path repositoryPath = tempDir.resolve("repo");
        Files.createDirectories(repositoryPath);
        runGit(repositoryPath, "git", "init", "-b", "main");
        Files.writeString(repositoryPath.resolve("README.md"), "hello");
        runGit(repositoryPath, "git", "add", "README.md");
        runGit(
                repositoryPath,
                "git",
                "-c",
                "user.name=Setup User",
                "-c",
                "user.email=setup@example.com",
                "commit",
                "-m",
                "initial commit"
        );

        GitWorkspaceSnapshot cleanSnapshot = service.inspectWorkspace(repositoryPath);
        assertThat(cleanSnapshot.branchName()).isEqualTo("main");
        assertThat(cleanSnapshot.clean()).isTrue();
        assertThat(cleanSnapshot.changedFiles()).isEmpty();

        Files.writeString(repositoryPath.resolve("README.md"), "hello world");
        Files.writeString(repositoryPath.resolve("src.txt"), "new file");

        GitWorkspaceSnapshot dirtySnapshot = service.inspectWorkspace(repositoryPath);
        assertThat(dirtySnapshot.clean()).isFalse();
        assertThat(dirtySnapshot.changedFiles()).contains("README.md", "src.txt");

        String commitSha = service.commitAllChanges(repositoryPath, "agentteam: commit changes");

        assertThat(commitSha).isNotBlank();
        GitWorkspaceSnapshot afterCommit = service.inspectWorkspace(repositoryPath);
        assertThat(afterCommit.clean()).isTrue();
        assertThat(afterCommit.headCommit()).isEqualTo(commitSha);
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
