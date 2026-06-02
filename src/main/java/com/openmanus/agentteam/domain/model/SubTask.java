package com.openmanus.agentteam.domain.model;

import lombok.Getter;

/**
 * Runtime subtask tracked by the agent-team scheduler.
 */
@Getter
public class SubTask {

    private final String taskId;
    private final String groupId;
    private final String parentSessionId;
    private final String title;
    private final String description;
    private final String contextSummary;
    private final long createdAt;
    private TaskStatus status;
    private String assignedAgentId;
    private String resultSummary;
    private String resultDetail;
    private String errorMessage;
    private long claimedAt;
    private long startedAt;
    private long finishedAt;

    public SubTask(
            String taskId,
            String groupId,
            String parentSessionId,
            String title,
            String description,
            String contextSummary,
            long createdAt
    ) {
        this.taskId = taskId;
        this.groupId = groupId;
        this.parentSessionId = parentSessionId == null ? "" : parentSessionId.trim();
        this.title = title == null ? "" : title.trim();
        this.description = description == null ? "" : description.trim();
        this.contextSummary = contextSummary == null ? "" : contextSummary.trim();
        this.createdAt = createdAt;
        this.status = TaskStatus.PENDING;
    }

    public void claim(String agentId, long timestamp) {
        this.assignedAgentId = agentId;
        this.claimedAt = timestamp;
        this.status = TaskStatus.CLAIMED;
    }

    public void markRunning(long timestamp) {
        this.startedAt = timestamp;
        this.status = TaskStatus.RUNNING;
    }

    public void markSucceeded(String summary, String detail, long timestamp) {
        this.resultSummary = summary;
        this.resultDetail = detail;
        this.errorMessage = null;
        this.finishedAt = timestamp;
        this.status = TaskStatus.SUCCEEDED;
    }

    public void markFailed(String error, long timestamp) {
        this.errorMessage = error;
        this.finishedAt = timestamp;
        this.status = TaskStatus.FAILED;
    }
}
