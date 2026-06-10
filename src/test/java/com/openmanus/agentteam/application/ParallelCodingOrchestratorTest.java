package com.openmanus.agentteam.application;

import com.openmanus.agentteam.domain.model.CodeSubTask;
import com.openmanus.agentteam.domain.model.GitRepositoryRuntime;
import com.openmanus.agentteam.domain.model.GitWorkspaceSnapshot;
import com.openmanus.agentteam.domain.model.GitWorktreeInfo;
import com.openmanus.agentteam.domain.model.ParallelCodingExecutionResult;
import com.openmanus.agentteam.domain.model.ParallelCodingPlan;
import com.openmanus.agentteam.domain.model.SubAgentCodingResult;
import com.openmanus.agentteam.domain.model.SubAgentCodingStatus;
import com.openmanus.agentteam.domain.port.GitWorktreeProvisioningPort;
import com.openmanus.agentteam.domain.port.GitWorkspacePort;
import com.openmanus.domain.service.AgentExecutionPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ParallelCodingOrchestrator Tests")
class ParallelCodingOrchestratorTest {

    @Test
    @DisplayName("should throw ParallelCodingException when git runtime is unavailable instead of falling back to Docker")
    void shouldThrowExceptionWhenGitRuntimeIsUnavailable() {
        RecordingAgentExecutionPort agentExecutionPort = new RecordingAgentExecutionPort();
        agentExecutionPort.syncResponse = "single-agent-result";
        ParallelCodingOrchestrator orchestrator = new ParallelCodingOrchestrator(
                agentExecutionPort,
                new ParallelCodingPlanner(),
                new RecordingGitWorktreePort(new GitRepositoryRuntime(
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        false,
                        "git executable not found"
                )),
                new RecordingSubAgentCodingExecutionService(),
                new RecordingIntegrationCoordinator(),
                5
        );

        assertThatThrownBy(() -> orchestrator.execute("Implement feature", "conv-1", Path.of("/repo")))
                .isInstanceOf(ParallelCodingException.class)
                .hasMessageContaining("代码执行无法启动")
                .hasMessageContaining("Git worktree");
    }

    @Test
    @DisplayName("should orchestrate parallel worktree subtasks and aggregate results")
    void shouldOrchestrateParallelWorktreeSubtasksAndAggregateResults() {
        RecordingAgentExecutionPort agentExecutionPort = new RecordingAgentExecutionPort();
        RecordingGitWorktreePort gitPort = new RecordingGitWorktreePort(new GitRepositoryRuntime(
                true,
                true,
                "git version 2.x",
                "/repo",
                "main",
                "head-1",
                true,
                null
        ));
        FixedPlanner planner = new FixedPlanner(new ParallelCodingPlan(
                true,
                "Independent coding tasks",
                List.of(
                        new CodeSubTask("task-a", "Backend", "Implement backend API", List.of("src/main/java/"), List.of(), List.of("compile"), List.of(), "low"),
                        new CodeSubTask("task-b", "Frontend", "Implement frontend UI", List.of("frontend/"), List.of(), List.of("npm test"), List.of(), "low")
                )
        ));
        RecordingSubAgentCodingExecutionService executionService = new RecordingSubAgentCodingExecutionService();
        executionService.resultsByTaskId.put("task-a", new SubAgentCodingResult(
                "task-a", SubAgentCodingStatus.SUCCEEDED, "backend done", List.of("src/main/java/Foo.java"),
                "agentteam/branch-a", "commit-a", "/repo/.agentteam/worktrees/task-a", Boolean.TRUE, "compile", "out", null
        ));
        executionService.resultsByTaskId.put("task-b", new SubAgentCodingResult(
                "task-b", SubAgentCodingStatus.SUCCEEDED, "frontend done", List.of("frontend/src/App.tsx"),
                "agentteam/branch-b", "commit-b", "/repo/.agentteam/worktrees/task-b", Boolean.TRUE, "npm test", "out", null
        ));
        ParallelCodingOrchestrator orchestrator = new ParallelCodingOrchestrator(
                agentExecutionPort,
                planner,
                gitPort,
                executionService,
                new RecordingIntegrationCoordinator(),
                5
        );

        ParallelCodingExecutionResult result = orchestrator.execute("""
                - backend
                - frontend
                """, "conv-2", Path.of("/repo"));

        assertThat(result.success()).isTrue();
        assertThat(result.fallbackToSingleAgent()).isFalse();
        assertThat(result.taskGroup()).isNotNull();
        assertThat(result.subAgentResults()).hasSize(2);
        assertThat(result.summary()).contains("status: SUCCEEDED");
        assertThat(result.integrationResult()).isNotNull();
        assertThat(result.integrationResult().success()).isTrue();
        assertThat(gitPort.createdBranches).hasSize(2);
        assertThat(executionService.seenBranches).hasSize(2);
    }

    @Test
    @DisplayName("should preserve partial failure instead of hiding failed subtask")
    void shouldPreservePartialFailureInsteadOfHidingFailedSubtask() {
        RecordingAgentExecutionPort agentExecutionPort = new RecordingAgentExecutionPort();
        RecordingGitWorktreePort gitPort = new RecordingGitWorktreePort(new GitRepositoryRuntime(
                true,
                true,
                "git version 2.x",
                "/repo",
                "main",
                "head-1",
                true,
                null
        ));
        FixedPlanner planner = new FixedPlanner(new ParallelCodingPlan(
                true,
                "Independent coding tasks",
                List.of(
                        new CodeSubTask("task-a", "Backend", "Implement backend API", List.of("src/main/java/"), List.of(), List.of("compile"), List.of(), "low"),
                        new CodeSubTask("task-b", "Frontend", "Implement frontend UI", List.of("frontend/"), List.of(), List.of("npm test"), List.of(), "low")
                )
        ));
        RecordingSubAgentCodingExecutionService executionService = new RecordingSubAgentCodingExecutionService();
        executionService.resultsByTaskId.put("task-a", new SubAgentCodingResult(
                "task-a", SubAgentCodingStatus.SUCCEEDED, "backend done", List.of("src/main/java/Foo.java"),
                "agentteam/branch-a", "commit-a", "/repo/.agentteam/worktrees/task-a", Boolean.TRUE, "compile", "out", null
        ));
        executionService.failTaskIds.add("task-b");
        ParallelCodingOrchestrator orchestrator = new ParallelCodingOrchestrator(
                agentExecutionPort,
                planner,
                gitPort,
                executionService,
                new RecordingIntegrationCoordinator(),
                5
        );

        ParallelCodingExecutionResult result = orchestrator.execute("""
                - backend
                - frontend
                """, "conv-3", Path.of("/repo"));

        assertThat(result.success()).isFalse();
        assertThat(result.summary()).contains("status: PARTIAL_FAILED");
        assertThat(result.summary()).contains("error=simulated subtask failure");
        assertThat(result.summary()).contains("worktree=/repo/.agentteam/worktrees/");
        assertThat(result.subAgentResults()).extracting(SubAgentCodingResult::status)
                .contains(SubAgentCodingStatus.FAILED);
    }

    private static final class RecordingIntegrationCoordinator extends IntegrationCoordinator {
        private RecordingIntegrationCoordinator() {
            super(new com.openmanus.agentteam.domain.port.GitIntegrationPort() {
                @Override
                public String createIntegrationBranch(Path repositoryPath, String branchName, String baseRef) {
                    return branchName;
                }

                @Override
                public void cherryPickCommit(Path repositoryPath, String commitSha) {
                }
            }, (workingDirectory, command) -> new com.openmanus.agentteam.domain.port.CommandExecutionPort.CommandExecutionResult(0, "compile ok", ""));
        }
    }

    private static final class FixedPlanner extends ParallelCodingPlanner {
        private final ParallelCodingPlan plan;

        private FixedPlanner(ParallelCodingPlan plan) {
            this.plan = plan;
        }

        @Override
        public ParallelCodingPlan plan(String userInput, int maxSubTasks) {
            return plan;
        }
    }

    private static final class RecordingAgentExecutionPort implements AgentExecutionPort {
        private String syncResponse;

        @Override
        public CompletableFuture<String> execute(String userInput, String conversationId) {
            return CompletableFuture.completedFuture(syncResponse);
        }

        @Override
        public String executeSync(String userInput, String conversationId) {
            return syncResponse;
        }
    }

    private static final class RecordingGitWorktreePort implements GitWorktreeProvisioningPort {
        private final GitRepositoryRuntime runtime;
        private final List<String> createdBranches = Collections.synchronizedList(new ArrayList<>());

        private RecordingGitWorktreePort(GitRepositoryRuntime runtime) {
            this.runtime = runtime;
        }

        @Override
        public GitRepositoryRuntime inspectRepository(Path repositoryPath) {
            return runtime;
        }

        @Override
        public List<GitWorktreeInfo> listWorktrees(Path repositoryPath) {
            return List.of();
        }

        @Override
        public GitWorktreeInfo createWorktree(Path repositoryPath, Path worktreePath, String branchName, String baseRef) {
            createdBranches.add(branchName);
            return new GitWorktreeInfo(worktreePath.toString(), "refs/heads/" + branchName, "head-1", false);
        }

        @Override
        public void removeWorktree(Path repositoryPath, Path worktreePath, boolean force) {
        }
    }

    private static final class RecordingSubAgentCodingExecutionService extends SubAgentCodingExecutionService {
        private final Map<String, SubAgentCodingResult> resultsByTaskId = new HashMap<>();
        private final List<String> seenBranches = Collections.synchronizedList(new ArrayList<>());
        private final List<String> failTaskIds = new ArrayList<>();

        private RecordingSubAgentCodingExecutionService() {
            super((context, input) -> "unused", new GitWorkspacePort() {
                @Override
                public GitWorkspaceSnapshot inspectWorkspace(Path worktreePath) {
                    return null;
                }

                @Override
                public String commitAllChanges(Path worktreePath, String commitMessage) {
                    return null;
                }
            });
        }

        @Override
        public SubAgentCodingResult execute(SubAgentCodingExecutionRequest request) {
            seenBranches.add(request.worktreeSession().branchName());
            if (failTaskIds.contains(request.subTask().taskId())) {
                throw new IllegalStateException("simulated subtask failure");
            }
            return resultsByTaskId.get(request.subTask().taskId());
        }
    }
}
