package com.openmanus.agentteam.application;

import com.openmanus.agentteam.domain.model.CodeSubTask;
import com.openmanus.agentteam.domain.model.GitWorkspaceSnapshot;
import com.openmanus.agentteam.domain.model.SubAgentCodingResult;
import com.openmanus.agentteam.domain.model.SubAgentCodingStatus;
import com.openmanus.agentteam.domain.model.WorktreeSession;
import com.openmanus.agentteam.domain.port.GitWorkspacePort;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.List;

/**
 * Executes one code-oriented subtask inside an isolated worktree context.
 */
@Slf4j
public class SubAgentCodingExecutionService {

    private final AgentTeamRoleExecutionPort roleExecutionPort;
    private final GitWorkspacePort gitWorkspacePort;

    public SubAgentCodingExecutionService(
            AgentTeamRoleExecutionPort roleExecutionPort,
            GitWorkspacePort gitWorkspacePort
    ) {
        this.roleExecutionPort = roleExecutionPort;
        this.gitWorkspacePort = gitWorkspacePort;
    }

    public SubAgentCodingResult execute(SubAgentCodingExecutionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        CodeSubTask subTask = request.subTask();
        WorktreeSession worktreeSession = request.worktreeSession();
        validate(subTask, worktreeSession);
        Path worktreePath = Path.of(worktreeSession.worktreePath());

        String prompt = buildPrompt(subTask, worktreeSession);
        try {
            log.info(
                    "SubAgentCodingExecution dispatching worktree-scoped task: taskId={}, branch={}, worktreePath={}, verificationCommands={}",
                    subTask.taskId(),
                    worktreeSession.branchName(),
                    worktreeSession.worktreePath(),
                    subTask.verificationCommands()
            );
            String rawOutput = roleExecutionPort.executeSync(
                    AgentTeamRole.SUB_AGENT,
                    prompt,
                    worktreeSession.sessionId()
            );
            GitWorkspaceSnapshot workspaceSnapshot = gitWorkspacePort.inspectWorkspace(worktreePath);
            String commitSha = workspaceSnapshot.clean()
                    ? workspaceSnapshot.headCommit()
                    : gitWorkspacePort.commitAllChanges(worktreePath, commitMessage(subTask, worktreeSession));
            GitWorkspaceSnapshot committedSnapshot = gitWorkspacePort.inspectWorkspace(worktreePath);
            log.info(
                    "SubAgentCodingExecution finished worktree task: taskId={}, branch={}, worktreePath={}, changedFiles={}, commitSha={}",
                    subTask.taskId(),
                    worktreeSession.branchName(),
                    worktreeSession.worktreePath(),
                    committedSnapshot.changedFiles(),
                    commitSha
            );
            return new SubAgentCodingResult(
                    subTask.taskId(),
                    SubAgentCodingStatus.SUCCEEDED,
                    summarize(rawOutput),
                    committedSnapshot.changedFiles(),
                    worktreeSession.branchName(),
                    commitSha,
                    worktreeSession.worktreePath(),
                    !subTask.verificationCommands().isEmpty(),
                    verificationHint(subTask),
                    rawOutput,
                    null
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "SubAgentCodingExecution failed: taskId={}, branch={}, worktreePath={}, error={}",
                    subTask.taskId(),
                    worktreeSession.branchName(),
                    worktreeSession.worktreePath(),
                    exception.getMessage()
            );
            log.warn(
                    "SubAgentCodingExecution failure summary: taskId={}, branch={}, worktreePath={}, verificationCommands={}",
                    subTask.taskId(),
                    worktreeSession.branchName(),
                    worktreeSession.worktreePath(),
                    subTask.verificationCommands()
            );
            return new SubAgentCodingResult(
                    subTask.taskId(),
                    SubAgentCodingStatus.FAILED,
                    "Sub-agent coding execution failed",
                    List.of(),
                    worktreeSession.branchName(),
                    null,
                    worktreeSession.worktreePath(),
                    false,
                    verificationHint(subTask),
                    "",
                    exception.getMessage()
            );
        }
    }

    private void validate(CodeSubTask subTask, WorktreeSession worktreeSession) {
        if (subTask == null) {
            throw new IllegalArgumentException("subTask must not be null");
        }
        if (worktreeSession == null) {
            throw new IllegalArgumentException("worktreeSession must not be null");
        }
        validateText(subTask.taskId(), "subTask.taskId");
        validateText(subTask.goal(), "subTask.goal");
        validateText(worktreeSession.sessionId(), "worktreeSession.sessionId");
        validateText(worktreeSession.branchName(), "worktreeSession.branchName");
        validateText(worktreeSession.worktreePath(), "worktreeSession.worktreePath");
    }

    private void validateText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private String buildPrompt(CodeSubTask subTask, WorktreeSession worktreeSession) {
        return """
                You are executing one isolated coding subtask inside an agent team.

                Task ID: %s
                Title: %s
                Goal: %s
                Branch: %s
                Base Ref: %s
                Worktree Path: %s

                Owned Paths:
                %s

                Forbidden Paths:
                %s

                Verification Commands:
                %s

                Dependency Hints:
                %s

                Conflict Risk: %s

                Execution Rules:
                1. Work only inside the current subtask boundary.
                2. Treat the worktree path above as your primary code workspace.
                3. Prefer explicit cwd/worktree paths when using shell-based file inspection or verification.
                4. Do not re-decompose the task.
                5. Do not delegate to another agent.
                6. Return a concise engineering summary, including files touched and verification outcome if available.
                """
                .formatted(
                        subTask.taskId(),
                        safe(subTask.title()),
                        subTask.goal().trim(),
                        worktreeSession.branchName().trim(),
                        safe(worktreeSession.baseRef()),
                        worktreeSession.worktreePath().trim(),
                        renderList(subTask.ownedPaths(), "- no explicit owned paths"),
                        renderList(subTask.forbiddenPaths(), "- no explicit forbidden paths"),
                        renderList(subTask.verificationCommands(), "- no verification commands specified"),
                        renderList(subTask.dependsOn(), "- no explicit dependencies"),
                        safe(subTask.conflictRisk())
                );
    }

    private String summarize(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return "Sub-agent completed but returned no usable content";
        }
        String compact = rawOutput.trim();
        return compact.length() > 120 ? compact.substring(0, 120) : compact;
    }

    private String verificationHint(CodeSubTask subTask) {
        if (subTask.verificationCommands().isEmpty()) {
            return "No verification commands were provided";
        }
        return "Planned verification commands: " + String.join(" | ", subTask.verificationCommands());
    }

    private String commitMessage(CodeSubTask subTask, WorktreeSession worktreeSession) {
        return "agentteam: complete " + subTask.taskId() + " on " + worktreeSession.branchName();
    }

    private String renderList(List<String> values, String fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ").append(value.trim());
        }
        return builder.isEmpty() ? fallback : builder.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
