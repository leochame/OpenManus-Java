package com.openmanus.agentteam.infra;

import com.openmanus.agentteam.application.InvalidTaskStateTransitionException;
import com.openmanus.agentteam.application.TaskOwnershipViolationException;
import com.openmanus.agentteam.domain.model.SubTask;
import com.openmanus.agentteam.domain.model.TaskStatus;
import com.openmanus.agentteam.domain.port.TaskGroupRepositoryPort;
import com.openmanus.agentteam.domain.port.TaskPoolPort;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory task pool with a locked claim section to prevent duplicate claim.
 */
@Slf4j
public class InMemoryTaskPool implements TaskPoolPort {

    private final ConcurrentLinkedQueue<String> pendingQueue = new ConcurrentLinkedQueue<>();
    private final TaskGroupRepositoryPort repository;
    private final ReentrantLock claimLock = new ReentrantLock();

    public InMemoryTaskPool(TaskGroupRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public void submit(SubTask subTask) {
        if (subTask == null) {
            return;
        }
        repository.saveSubTask(subTask);
        pendingQueue.offer(subTask.getTaskId());
        log.info(
                "TaskPool submit: groupId={}, taskId={}, title={}, pendingQueueSize={}",
                subTask.getGroupId(),
                subTask.getTaskId(),
                subTask.getTitle(),
                pendingQueue.size()
        );
    }

    @Override
    public Optional<SubTask> claimNext(String agentId) {
        claimLock.lock();
        try {
            while (!pendingQueue.isEmpty()) {
                String taskId = pendingQueue.poll();
                if (taskId == null) {
                    break;
                }
                Optional<SubTask> maybeTask = repository.findSubTask(taskId);
                if (maybeTask.isEmpty()) {
                    continue;
                }
                SubTask subTask = maybeTask.get();
                if (subTask.getStatus() != TaskStatus.PENDING) {
                    continue;
                }
                subTask.claim(agentId, System.currentTimeMillis());
                repository.saveSubTask(subTask);
                log.info(
                        "TaskPool claim success: agentId={}, groupId={}, taskId={}, title={}",
                        agentId,
                        subTask.getGroupId(),
                        subTask.getTaskId(),
                        subTask.getTitle()
                );
                return Optional.of(subTask);
            }
            return Optional.empty();
        } finally {
            claimLock.unlock();
        }
    }

    @Override
    public void markRunning(String taskId, String agentId) {
        SubTask task = requireOwnedTask(taskId, agentId);
        requireStatus(task, TaskStatus.CLAIMED, "Only claimed task can be marked running");
        task.markRunning(System.currentTimeMillis());
        repository.saveSubTask(task);
        log.info(
                "TaskPool mark running: agentId={}, groupId={}, taskId={}, title={}",
                agentId,
                task.getGroupId(),
                task.getTaskId(),
                task.getTitle()
        );
    }

    @Override
    public void markSucceeded(String taskId, String agentId, String summary, String detail) {
        SubTask task = requireOwnedTask(taskId, agentId);
        requireStatus(task, TaskStatus.RUNNING, "Only running task can be marked succeeded");
        task.markSucceeded(summary, detail, System.currentTimeMillis());
        repository.saveSubTask(task);
        log.info(
                "TaskPool mark succeeded: agentId={}, groupId={}, taskId={}, title={}, summary={}",
                task.getAssignedAgentId(),
                task.getGroupId(),
                task.getTaskId(),
                task.getTitle(),
                summarize(summary)
        );
    }

    @Override
    public void markFailed(String taskId, String agentId, String errorMessage) {
        SubTask task = requireOwnedTask(taskId, agentId);
        requireStatus(task, TaskStatus.RUNNING, "Only running task can be marked failed");
        task.markFailed(errorMessage, System.currentTimeMillis());
        repository.saveSubTask(task);
        log.warn(
                "TaskPool mark failed: agentId={}, groupId={}, taskId={}, title={}, error={}",
                task.getAssignedAgentId(),
                task.getGroupId(),
                task.getTaskId(),
                task.getTitle(),
                summarize(errorMessage)
        );
    }

    @Override
    public Optional<SubTask> findById(String taskId) {
        return repository.findSubTask(taskId);
    }

    @Override
    public List<SubTask> findByGroupId(String groupId) {
        return repository.findSubTasksByGroupId(groupId);
    }

    private SubTask requireOwnedTask(String taskId, String agentId) {
        SubTask task = repository.findSubTask(taskId)
                .orElseThrow(() -> new InvalidTaskStateTransitionException("Task not found: " + taskId));
        if (task.getAssignedAgentId() == null || task.getAssignedAgentId().isBlank()) {
            throw new InvalidTaskStateTransitionException("Task has not been claimed yet: " + taskId);
        }
        if (!task.getAssignedAgentId().equals(agentId)) {
            throw new TaskOwnershipViolationException("Task is not owned by agent: " + agentId);
        }
        if (task.getStatus().isTerminal()) {
            throw new InvalidTaskStateTransitionException("Task already finished: " + taskId);
        }
        return task;
    }

    private void requireStatus(SubTask task, TaskStatus expectedStatus, String message) {
        if (task.getStatus() != expectedStatus) {
            throw new InvalidTaskStateTransitionException(message + ", currentStatus=" + task.getStatus());
        }
    }

    private String summarize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
    }
}
