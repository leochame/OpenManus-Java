package com.openmanus.agentteam.domain.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Group of parallel subtasks emitted from one user request.
 */
@Getter
public class TaskGroup {

    private final String groupId;
    private final String parentTaskId;
    private final String masterAgentId;
    private final String originalUserRequest;
    private final long createdAt;
    private final List<String> subTaskIds = new ArrayList<>();
    private TaskGroupStatus status;
    private long updatedAt;

    public TaskGroup(
            String groupId,
            String parentTaskId,
            String masterAgentId,
            String originalUserRequest,
            long createdAt
    ) {
        this.groupId = groupId;
        this.parentTaskId = parentTaskId;
        this.masterAgentId = masterAgentId;
        this.originalUserRequest = originalUserRequest == null ? "" : originalUserRequest.trim();
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.status = TaskGroupStatus.CREATED;
    }

    public List<String> getSubTaskIds() {
        return Collections.unmodifiableList(subTaskIds);
    }

    public void addSubTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        this.subTaskIds.add(taskId);
        this.updatedAt = System.currentTimeMillis();
    }

    public void updateStatus(TaskGroupStatus newStatus, long timestamp) {
        this.status = newStatus == null ? this.status : newStatus;
        this.updatedAt = timestamp;
    }
}
