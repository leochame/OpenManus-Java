package com.openmanus.agentteam.application;

import com.openmanus.agentteam.domain.model.CodeSubTask;
import com.openmanus.agentteam.domain.model.CodeTaskGroup;
import com.openmanus.agentteam.domain.model.GitRepositoryRuntime;
import com.openmanus.agentteam.domain.model.GitWorktreeInfo;
import com.openmanus.agentteam.domain.model.ParallelCodingExecutionResult;
import com.openmanus.agentteam.domain.model.ParallelCodingPlan;
import com.openmanus.agentteam.domain.model.SubAgentCodingResult;
import com.openmanus.agentteam.domain.model.SubAgentCodingStatus;
import com.openmanus.agentteam.domain.model.WorktreeSession;
import com.openmanus.agentteam.domain.port.GitWorktreeProvisioningPort;
import com.openmanus.domain.service.AgentExecutionPort;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates isolated parallel coding subtasks through Git worktrees.
 */
@Slf4j
public class ParallelCodingOrchestrator {

    private final AgentExecutionPort agentExecutionPort;
    private final ParallelCodingPlanner planner;
    private final GitWorktreeProvisioningPort gitWorktreeProvisioningPort;
    private final SubAgentCodingExecutionService subAgentCodingExecutionService;
    private final IntegrationCoordinator integrationCoordinator;
    private final int maxSubTasksPerGroup;

    public ParallelCodingOrchestrator(
            AgentExecutionPort agentExecutionPort,
            ParallelCodingPlanner planner,
            GitWorktreeProvisioningPort gitWorktreeProvisioningPort,
            SubAgentCodingExecutionService subAgentCodingExecutionService,
            IntegrationCoordinator integrationCoordinator,
            int maxSubTasksPerGroup
    ) {
        this.agentExecutionPort = agentExecutionPort;
        this.planner = planner;
        this.gitWorktreeProvisioningPort = gitWorktreeProvisioningPort;
        this.subAgentCodingExecutionService = subAgentCodingExecutionService;
        this.integrationCoordinator = integrationCoordinator;
        this.maxSubTasksPerGroup = maxSubTasksPerGroup;
    }

    public ParallelCodingExecutionResult execute(String userInput, String conversationId, Path repositoryPath) {
        GitRepositoryRuntime runtime = gitWorktreeProvisioningPort.inspectRepository(repositoryPath);
        if (!runtime.supportsWorktreeOperations()) {
            String reason = runtime.failureReason() == null ? "git worktree mode unavailable" : runtime.failureReason();
            log.warn("ParallelCodingOrchestrator falling back because git runtime is unavailable: reason={}", reason);
            String fallback = agentExecutionPort.executeSync(userInput, conversationId);
            return new ParallelCodingExecutionResult(
                    true,
                    true,
                    "Fell back to single-agent execution because worktree mode is unavailable: " + reason,
                    fallback,
                    null,
                    List.of(),
                    null
            );
        }

        ParallelCodingPlan plan = planner.plan(userInput, maxSubTasksPerGroup);
        log.info(
                "ParallelCodingOrchestrator plan finished: parallelizable={}, subTaskCount={}, reason={}",
                plan.parallelizable(),
                plan.subTasks().size(),
                plan.reason()
        );
        if (!plan.parallelizable()) {
            String fallback = agentExecutionPort.executeSync(userInput, conversationId);
            return new ParallelCodingExecutionResult(
                    true,
                    true,
                    "Fell back to single-agent execution because plan is not safely parallelizable: " + plan.reason(),
                    fallback,
                    null,
                    List.of(),
                    null
            );
        }

        String groupId = "coding-group-" + UUID.randomUUID();
        CodeTaskGroup taskGroup = new CodeTaskGroup(
                groupId,
                conversationId,
                userInput,
                plan.subTasks()
        );
        log.info(
                "ParallelCodingOrchestrator created coding task group: groupId={}, conversationId={}, subTaskCount={}",
                groupId,
                conversationId,
                taskGroup.subTasks().size()
        );

        List<SubAgentCodingResult> results = executeParallel(repositoryPath, taskGroup);
        long failedCount = results.stream().filter(result -> result.status() == SubAgentCodingStatus.FAILED).count();
        var integrationResult = failedCount == 0 ? integrationCoordinator.integrate(repositoryPath, results) : null;
        boolean success = failedCount == 0 && integrationResult != null && integrationResult.success();
        String summary = buildSummary(taskGroup, results, plan.reason(), success, integrationResult);
        log.info(
                "ParallelCodingOrchestrator completed: groupId={}, success={}, failedCount={}, branches={}, integrationBranch={}",
                groupId,
                success,
                failedCount,
                results.stream().map(SubAgentCodingResult::branchName).toList(),
                integrationResult == null ? null : integrationResult.integrationBranch()
        );
        return new ParallelCodingExecutionResult(
                success,
                false,
                summary,
                null,
                taskGroup,
                results,
                integrationResult
        );
    }

    private List<SubAgentCodingResult> executeParallel(Path repositoryPath, CodeTaskGroup taskGroup) {
        int threadCount = Math.max(1, taskGroup.subTasks().size());
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        try {
            List<CompletableFuture<SubAgentCodingResult>> futures = new ArrayList<>();
            for (CodeSubTask subTask : taskGroup.subTasks()) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> executeSingleSubTask(repositoryPath, taskGroup, subTask),
                        executorService
                ));
            }
            return futures.stream()
                    .map(CompletableFuture::join)
                    .sorted(Comparator.comparing(SubAgentCodingResult::taskId))
                    .toList();
        } finally {
            executorService.shutdownNow();
        }
    }

    private SubAgentCodingResult executeSingleSubTask(Path repositoryPath, CodeTaskGroup taskGroup, CodeSubTask subTask) {
        String branchName = buildBranchName(taskGroup.groupId(), subTask);
        Path worktreePath = repositoryPath.resolve(".agentteam")
                .resolve("worktrees")
                .resolve(taskGroup.groupId())
                .resolve(subTask.taskId());
        String sessionId = taskGroup.groupId() + "-" + subTask.taskId();
        log.info(
                "ParallelCodingOrchestrator provisioning worktree: groupId={}, taskId={}, branch={}, worktreePath={}",
                taskGroup.groupId(),
                subTask.taskId(),
                branchName,
                worktreePath
        );
        try {
            GitWorktreeInfo worktreeInfo = gitWorktreeProvisioningPort.createWorktree(
                    repositoryPath,
                    worktreePath,
                    branchName,
                    "HEAD"
            );
            log.info(
                    "ParallelCodingOrchestrator worktree ready: groupId={}, taskId={}, branch={}, worktreePath={}, headCommit={}",
                    taskGroup.groupId(),
                    subTask.taskId(),
                    branchName,
                    worktreeInfo.path(),
                    worktreeInfo.headCommit()
            );
            WorktreeSession worktreeSession = new WorktreeSession(
                    sessionId,
                    branchName,
                    "HEAD",
                    worktreeInfo.path()
            );
            SubAgentCodingResult result = subAgentCodingExecutionService.execute(
                    new SubAgentCodingExecutionRequest(subTask, worktreeSession)
            );
            log.info(
                    "ParallelCodingOrchestrator subtask finished: groupId={}, taskId={}, branch={}, status={}, commitSha={}",
                    taskGroup.groupId(),
                    subTask.taskId(),
                    branchName,
                    result.status(),
                    result.commitSha()
            );
            return result;
        } catch (RuntimeException exception) {
            log.warn(
                    "ParallelCodingOrchestrator subtask failed before completion: groupId={}, taskId={}, branch={}, worktreePath={}, error={}",
                    taskGroup.groupId(),
                    subTask.taskId(),
                    branchName,
                    worktreePath,
                    exception.getMessage()
            );
            return new SubAgentCodingResult(
                    subTask.taskId(),
                    SubAgentCodingStatus.FAILED,
                    "Parallel coding subtask failed before completion",
                    List.of(),
                    branchName,
                    null,
                    worktreePath.toAbsolutePath().normalize().toString(),
                    false,
                    subTask.verificationCommands().isEmpty()
                            ? "No verification commands were provided"
                            : "Planned verification commands: " + String.join(" | ", subTask.verificationCommands()),
                    "",
                    exception.getMessage()
            );
        }
    }

    private String buildBranchName(String groupId, CodeSubTask subTask) {
        String normalizedTaskId = sanitize(subTask.taskId());
        return "agentteam/" + sanitize(groupId) + "-" + normalizedTaskId;
    }

    private String sanitize(String value) {
        String raw = value == null ? "task" : value.trim().toLowerCase();
        String sanitized = raw.replaceAll("[^a-z0-9._/-]+", "-");
        return sanitized.replaceAll("-{2,}", "-");
    }

    private String buildSummary(
            CodeTaskGroup taskGroup,
            List<SubAgentCodingResult> results,
            String planningReason,
            boolean success,
            com.openmanus.agentteam.domain.model.IntegrationResult integrationResult
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("Parallel coding execution finished.\n");
        builder.append("groupId: ").append(taskGroup.groupId()).append('\n');
        builder.append("conversationId: ").append(taskGroup.conversationId()).append('\n');
        builder.append("planningReason: ").append(planningReason).append('\n');
        builder.append("status: ").append(success ? "SUCCEEDED" : "PARTIAL_FAILED").append('\n');
        builder.append("success: ").append(results.stream().filter(result -> result.status() == SubAgentCodingStatus.SUCCEEDED).count()).append('\n');
        builder.append("failed: ").append(results.stream().filter(result -> result.status() == SubAgentCodingStatus.FAILED).count()).append('\n');
        builder.append("\nSubtasks:\n");
        for (SubAgentCodingResult result : results) {
            builder.append("- ").append(result.taskId())
                    .append(" [").append(result.status()).append("]")
                    .append(" branch=").append(result.branchName())
                    .append(" commit=").append(result.commitSha() == null ? "" : result.commitSha())
                    .append(" files=").append(result.changedFiles())
                    .append('\n');
        }
        if (integrationResult != null) {
            builder.append("\nIntegration:\n");
            builder.append("branch=").append(integrationResult.integrationBranch()).append('\n');
            builder.append("merged=").append(integrationResult.mergedBranches()).append('\n');
            builder.append("verification=").append(integrationResult.testSummary()).append('\n');
        }
        return builder.toString().trim();
    }
}
