package com.openmanus.agentteam.domain.service;

import com.openmanus.agentteam.domain.model.SubTask;
import com.openmanus.agentteam.domain.model.TaskGroupSnapshot;
import com.openmanus.agentteam.domain.model.TaskGroupStatus;
import com.openmanus.agentteam.domain.model.TaskStatus;

import java.util.List;

/**
 * Computes task-group progress from current subtask states.
 */
public class TaskGroupStatusCalculator {

    public TaskGroupSnapshot calculate(String groupId, List<SubTask> subTasks) {
        List<SubTask> tasks = subTasks == null ? List.of() : subTasks;
        int total = tasks.size();
        int pending = 0;
        int claimed = 0;
        int running = 0;
        int succeeded = 0;
        int failed = 0;

        for (SubTask task : tasks) {
            if (task == null || task.getStatus() == null) {
                continue;
            }
            TaskStatus status = task.getStatus();
            switch (status) {
                case PENDING -> pending++;
                case CLAIMED -> claimed++;
                case RUNNING -> running++;
                case SUCCEEDED -> succeeded++;
                case FAILED -> failed++;
                default -> {
                }
            }
        }

        TaskGroupStatus groupStatus = resolveStatus(total, pending, claimed, running, succeeded, failed);
        return new TaskGroupSnapshot(groupId, total, pending, claimed, running, succeeded, failed, groupStatus);
    }

    private TaskGroupStatus resolveStatus(
            int total,
            int pending,
            int claimed,
            int running,
            int succeeded,
            int failed
    ) {
        if (total == 0 || pending == total) {
            return TaskGroupStatus.CREATED;
        }
        if (succeeded == total) {
            return TaskGroupStatus.COMPLETED;
        }
        if (failed == total) {
            return TaskGroupStatus.FAILED;
        }
        if (succeeded + failed == total && failed > 0) {
            return TaskGroupStatus.PARTIAL_FAILED;
        }
        if (claimed > 0 || running > 0 || succeeded > 0 || failed > 0) {
            return TaskGroupStatus.RUNNING;
        }
        return TaskGroupStatus.CREATED;
    }
}
