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
import com.openmanus.domain.model.ExecutionErrorCodes;
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
            log.warn("ParallelCodingOrchestrator: worktree unavailable, rejecting request: reason={}", reason);
            throw new ParallelCodingException(
                    ExecutionErrorCodes.WORKTREE_UNAVAILABLE,
                    buildWorktreeUnavailableMessage(reason, repositoryPath)
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
            log.warn("ParallelCodingOrchestrator: plan not parallelizable, rejecting request: reason={}", plan.reason());
            throw new ParallelCodingException(
                    ExecutionErrorCodes.PLAN_NOT_PARALLELIZABLE,
                    buildPlanNotParallelizableMessage(plan.reason())
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
        try {
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
        } finally {
            cleanupWorktrees(repositoryPath, results);
        }
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
                "ParallelCodingOrchestrator provisioning worktree: groupId={}, taskId={}, branch={}, repositoryPath={}, worktreePath={}",
                taskGroup.groupId(),
                subTask.taskId(),
                branchName,
                repositoryPath,
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
                    "ParallelCodingOrchestrator subtask failed before completion: groupId={}, taskId={}, branch={}, worktreePath={}, errorType={}, error={}",
                    taskGroup.groupId(),
                    subTask.taskId(),
                    branchName,
                    worktreePath,
                    exception.getClass().getSimpleName(),
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
                    null,
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

    private void cleanupWorktrees(Path repositoryPath, List<SubAgentCodingResult> results) {
        for (SubAgentCodingResult result : results) {
            if (result.worktreePath() == null || result.worktreePath().isBlank()) {
                continue;
            }
            try {
                Path worktreePath = Path.of(result.worktreePath());
                log.info(
                        "ParallelCodingOrchestrator cleaning up worktree: taskId={}, branch={}, path={}",
                        result.taskId(),
                        result.branchName(),
                        worktreePath
                );
                gitWorktreeProvisioningPort.removeWorktree(repositoryPath, worktreePath, true);
            } catch (RuntimeException exception) {
                log.warn(
                        "ParallelCodingOrchestrator failed to clean up worktree: taskId={}, branch={}, path={}, error={}",
                        result.taskId(),
                        result.branchName(),
                        result.worktreePath(),
                        exception.getMessage()
                );
            }
        }
    }

    private String buildWorktreeUnavailableMessage(String reason, Path repositoryPath) {
        String path = repositoryPath.toAbsolutePath().normalize().toString();
        if (reason.contains("not a git repository") || reason.contains("is not a git")) {
            return "代码执行无法启动：所选路径 \"" + path + "\" 不是有效的 Git 仓库。请检查路径是否正确。";
        }
        if (reason.toLowerCase().contains("git command is not available")
                || reason.contains("git 命令不可用")) {
            return "代码执行无法启动：服务器上未安装 Git，无法创建隔离工作区。";
        }
        return "代码执行无法启动：Git worktree 操作不可用（原因：" + reason + "）。请检查仓库路径是否正确（路径：" + path + "）。";
    }

    private String buildPlanNotParallelizableMessage(String planningReason) {
        if (planningReason != null) {
            if (planningReason.toLowerCase().contains("fewer than two")
                    || planningReason.contains("子任务少于")) {
                return "代码执行无法并行化：请求中未检测到多个独立的编码子任务。"
                        + "请使用编号列表（如 1) 或 - 开头）明确列出多个并行子任务。";
            }
            if (planningReason.toLowerCase().contains("depend")
                    || planningReason.contains("依赖")) {
                return "代码执行无法并行化：检测到子任务之间存在依赖关系，无法安全并行执行。"
                        + "请将任务拆分为完全独立的子任务后重试。";
            }
        }
        return "代码执行无法并行化：" + (planningReason == null ? "任务无法安全拆分为独立子任务。" : planningReason)
                + " 请调整请求格式后重试。";
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
                    .append(" worktree=").append(result.worktreePath() == null ? "" : result.worktreePath())
                    .append(" files=").append(result.changedFiles())
                    .append('\n');
            if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
                builder.append("  error=").append(result.errorMessage()).append('\n');
            }
            if (result.testSummary() != null && !result.testSummary().isBlank()) {
                builder.append("  verification=").append(result.testSummary()).append('\n');
            }
        }
        if (integrationResult != null) {
            builder.append("\nIntegration:\n");
            builder.append("branch=").append(integrationResult.integrationBranch()).append('\n');
            builder.append("merged=").append(integrationResult.mergedBranches()).append('\n');
            builder.append("verification=").append(integrationResult.testSummary()).append('\n');
            if (integrationResult.errorMessage() != null && !integrationResult.errorMessage().isBlank()) {
                builder.append("error=").append(integrationResult.errorMessage()).append('\n');
            }
        }
        return builder.toString().trim();
    }
}
