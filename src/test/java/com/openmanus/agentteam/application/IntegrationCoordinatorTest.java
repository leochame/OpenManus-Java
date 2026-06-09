package com.openmanus.agentteam.application;

import com.openmanus.agentteam.domain.model.IntegrationResult;
import com.openmanus.agentteam.domain.model.SubAgentCodingResult;
import com.openmanus.agentteam.domain.model.SubAgentCodingStatus;
import com.openmanus.agentteam.domain.port.CommandExecutionPort;
import com.openmanus.agentteam.domain.port.GitIntegrationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IntegrationCoordinator Tests")
class IntegrationCoordinatorTest {

    @Test
    @DisplayName("should create integration branch cherry-pick commits and run verification")
    void shouldCreateIntegrationBranchCherryPickCommitsAndRunVerification() {
        RecordingGitIntegrationPort gitPort = new RecordingGitIntegrationPort();
        RecordingCommandExecutionPort commandPort = new RecordingCommandExecutionPort();
        commandPort.result = new CommandExecutionPort.CommandExecutionResult(0, "compile ok", "");
        IntegrationCoordinator coordinator = new IntegrationCoordinator(gitPort, commandPort);

        IntegrationResult result = coordinator.integrate(Path.of("/repo"), List.of(
                new SubAgentCodingResult("task-a", SubAgentCodingStatus.SUCCEEDED, "done", List.of("a"),
                        "agentteam/task-a", "commit-a", "/repo/wt-a", true, "compile", "raw", null),
                new SubAgentCodingResult("task-b", SubAgentCodingStatus.SUCCEEDED, "done", List.of("b"),
                        "agentteam/task-b", "commit-b", "/repo/wt-b", true, "compile", "raw", null)
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.integrationBranch()).startsWith("agentteam/integration-");
        assertThat(result.mergedBranches()).containsExactly("agentteam/task-a", "agentteam/task-b");
        assertThat(gitPort.cherryPickedCommits).containsExactly("commit-a", "commit-b");
        assertThat(commandPort.command).contains("compile");
    }

    @Test
    @DisplayName("should return failure when cherry-pick fails")
    void shouldReturnFailureWhenCherryPickFails() {
        RecordingGitIntegrationPort gitPort = new RecordingGitIntegrationPort();
        gitPort.failOnCommit = "commit-b";
        RecordingCommandExecutionPort commandPort = new RecordingCommandExecutionPort();
        IntegrationCoordinator coordinator = new IntegrationCoordinator(gitPort, commandPort);

        IntegrationResult result = coordinator.integrate(Path.of("/repo"), List.of(
                new SubAgentCodingResult("task-a", SubAgentCodingStatus.SUCCEEDED, "done", List.of("a"),
                        "agentteam/task-a", "commit-a", "/repo/wt-a", true, "compile", "raw", null),
                new SubAgentCodingResult("task-b", SubAgentCodingStatus.SUCCEEDED, "done", List.of("b"),
                        "agentteam/task-b", "commit-b", "/repo/wt-b", true, "compile", "raw", null)
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.mergedBranches()).containsExactly("agentteam/task-a");
        assertThat(result.errorMessage()).contains("commit-b");
    }

    private static final class RecordingGitIntegrationPort implements GitIntegrationPort {
        private final List<String> cherryPickedCommits = new ArrayList<>();
        private String failOnCommit;

        @Override
        public String createIntegrationBranch(Path repositoryPath, String branchName, String baseRef) {
            return branchName;
        }

        @Override
        public void cherryPickCommit(Path repositoryPath, String commitSha) {
            if (commitSha.equals(failOnCommit)) {
                throw new IllegalStateException("failed to cherry-pick " + commitSha);
            }
            cherryPickedCommits.add(commitSha);
        }
    }

    private static final class RecordingCommandExecutionPort implements CommandExecutionPort {
        private String command;
        private CommandExecutionResult result;

        @Override
        public CommandExecutionResult execute(Path workingDirectory, String command) {
            this.command = command;
            return result;
        }
    }
}
