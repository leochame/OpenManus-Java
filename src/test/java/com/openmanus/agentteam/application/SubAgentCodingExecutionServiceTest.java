package com.openmanus.agentteam.application;

import com.openmanus.agentteam.domain.model.CodeSubTask;
import com.openmanus.agentteam.domain.model.GitWorkspaceSnapshot;
import com.openmanus.agentteam.domain.model.SubAgentCodingResult;
import com.openmanus.agentteam.domain.model.SubAgentCodingStatus;
import com.openmanus.agentteam.domain.model.WorktreeSession;
import com.openmanus.agentteam.domain.port.GitWorkspacePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SubAgentCodingExecutionService Tests")
class SubAgentCodingExecutionServiceTest {

    @Test
    @DisplayName("should dispatch worktree-scoped prompt through sub-agent role")
    void shouldDispatchWorktreeScopedPromptThroughSubAgentRole() {
        RecordingRoleExecutionPort roleExecutionPort = new RecordingRoleExecutionPort();
        roleExecutionPort.response = "Updated src/main/java/Foo.java and ran mvn -q -DskipITs test";
        RecordingGitWorkspacePort gitWorkspacePort = new RecordingGitWorkspacePort();
        gitWorkspacePort.beforeCommitSnapshot = new GitWorkspaceSnapshot(
                "agentteam/task-1",
                "head-before",
                false,
                List.of("src/main/java/Foo.java", "src/test/java/FooTest.java")
        );
        gitWorkspacePort.afterCommitSnapshot = new GitWorkspaceSnapshot(
                "agentteam/task-1",
                "commit-123",
                true,
                List.of()
        );
        gitWorkspacePort.commitSha = "commit-123";
        SubAgentCodingExecutionService service = new SubAgentCodingExecutionService(roleExecutionPort, gitWorkspacePort);

        SubAgentCodingResult result = service.execute(new SubAgentCodingExecutionRequest(
                new CodeSubTask(
                        "task-1",
                        "Implement API field",
                        "Add new API field handling in backend service",
                        List.of("src/main/java/com/openmanus/api"),
                        List.of("frontend/src"),
                        List.of("mvn -q -DskipITs test -Dtest=ApiServiceTest"),
                        List.of(),
                        "low"
                ),
                new WorktreeSession(
                        "coding-session-1",
                        "agentteam/task-1",
                        "HEAD",
                        "E:\\Project\\OpenManus-Java\\.agentteam\\worktrees\\task-1"
                )
        ));

        assertThat(roleExecutionPort.role).isEqualTo(AgentTeamRole.SUB_AGENT);
        assertThat(roleExecutionPort.conversationId).isEqualTo("coding-session-1");
        assertThat(roleExecutionPort.input).contains("Worktree Path: E:\\Project\\OpenManus-Java\\.agentteam\\worktrees\\task-1");
        assertThat(roleExecutionPort.input).contains("Branch: agentteam/task-1");
        assertThat(roleExecutionPort.input).contains("Owned Paths:");
        assertThat(roleExecutionPort.input).contains("Verification Commands:");
        assertThat(result.status()).isEqualTo(SubAgentCodingStatus.SUCCEEDED);
        assertThat(result.branchName()).isEqualTo("agentteam/task-1");
        assertThat(result.worktreePath()).isEqualTo("E:\\Project\\OpenManus-Java\\.agentteam\\worktrees\\task-1");
        assertThat(result.summary()).contains("Updated src/main/java/Foo.java");
        assertThat(result.testSummary()).contains("ApiServiceTest");
        assertThat(result.changedFiles()).contains("src/main/java/Foo.java", "src/test/java/FooTest.java");
        assertThat(result.commitSha()).isEqualTo("commit-123");
        assertThat(gitWorkspacePort.commitMessage).contains("task-1");
        assertThat(gitWorkspacePort.committedPath).isEqualTo(Path.of("E:\\Project\\OpenManus-Java\\.agentteam\\worktrees\\task-1"));
    }

    @Test
    @DisplayName("should capture failure into structured coding result")
    void shouldCaptureFailureIntoStructuredCodingResult() {
        RecordingRoleExecutionPort roleExecutionPort = new RecordingRoleExecutionPort();
        roleExecutionPort.failure = new IllegalStateException("simulated runtime failure");
        RecordingGitWorkspacePort gitWorkspacePort = new RecordingGitWorkspacePort();
        SubAgentCodingExecutionService service = new SubAgentCodingExecutionService(roleExecutionPort, gitWorkspacePort);

        SubAgentCodingResult result = service.execute(new SubAgentCodingExecutionRequest(
                new CodeSubTask(
                        "task-2",
                        "Implement UI rendering",
                        "Render new field in frontend page",
                        List.of("frontend/src"),
                        List.of("src/main/java"),
                        List.of("npm test -- App"),
                        List.of(),
                        "medium"
                ),
                new WorktreeSession("coding-session-2", "agentteam/task-2", "HEAD", "/tmp/task-2")
        ));

        assertThat(result.status()).isEqualTo(SubAgentCodingStatus.FAILED);
        assertThat(result.errorMessage()).contains("simulated runtime failure");
        assertThat(result.branchName()).isEqualTo("agentteam/task-2");
        assertThat(result.commitSha()).isNull();
    }

    @Test
    @DisplayName("should reject blank worktree metadata")
    void shouldRejectBlankWorktreeMetadata() {
        SubAgentCodingExecutionService service = new SubAgentCodingExecutionService(
                (role, input, conversationId) -> "ok",
                new RecordingGitWorkspacePort()
        );

        assertThatThrownBy(() -> service.execute(new SubAgentCodingExecutionRequest(
                new CodeSubTask("task-3", "t", "goal", List.of(), List.of(), List.of(), List.of(), "low"),
                new WorktreeSession(" ", "agentteam/task-3", "HEAD", "/tmp/task-3")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("worktreeSession.sessionId");
    }

    @Test
    @DisplayName("should skip commit when worktree stays clean")
    void shouldSkipCommitWhenWorktreeStaysClean() {
        RecordingRoleExecutionPort roleExecutionPort = new RecordingRoleExecutionPort();
        roleExecutionPort.response = "No code changes required";
        RecordingGitWorkspacePort gitWorkspacePort = new RecordingGitWorkspacePort();
        gitWorkspacePort.beforeCommitSnapshot = new GitWorkspaceSnapshot(
                "agentteam/task-4",
                "head-clean",
                true,
                List.of()
        );
        gitWorkspacePort.afterCommitSnapshot = gitWorkspacePort.beforeCommitSnapshot;
        gitWorkspacePort.commitSha = "unused";
        SubAgentCodingExecutionService service = new SubAgentCodingExecutionService(roleExecutionPort, gitWorkspacePort);

        SubAgentCodingResult result = service.execute(new SubAgentCodingExecutionRequest(
                new CodeSubTask("task-4", "No-op", "Confirm no changes needed", List.of(), List.of(), List.of(), List.of(), "low"),
                new WorktreeSession("coding-session-4", "agentteam/task-4", "HEAD", "/tmp/task-4")
        ));

        assertThat(result.commitSha()).isEqualTo("head-clean");
        assertThat(gitWorkspacePort.commitInvocations).isZero();
    }

    private static final class RecordingRoleExecutionPort implements AgentTeamRoleExecutionPort {
        private AgentTeamRole role;
        private String input;
        private String conversationId;
        private String response;
        private RuntimeException failure;

        @Override
        public String executeSync(AgentTeamRole role, String input, String conversationId) {
            this.role = role;
            this.input = input;
            this.conversationId = conversationId;
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }

    private static final class RecordingGitWorkspacePort implements GitWorkspacePort {
        private GitWorkspaceSnapshot beforeCommitSnapshot;
        private GitWorkspaceSnapshot afterCommitSnapshot;
        private String commitSha;
        private int inspectInvocations;
        private int commitInvocations;
        private String commitMessage;
        private Path committedPath;

        @Override
        public GitWorkspaceSnapshot inspectWorkspace(Path worktreePath) {
            inspectInvocations++;
            if (inspectInvocations <= 1) {
                return beforeCommitSnapshot;
            }
            return afterCommitSnapshot == null ? beforeCommitSnapshot : afterCommitSnapshot;
        }

        @Override
        public String commitAllChanges(Path worktreePath, String commitMessage) {
            this.commitInvocations++;
            this.commitMessage = commitMessage;
            this.committedPath = worktreePath;
            return commitSha;
        }
    }
}
