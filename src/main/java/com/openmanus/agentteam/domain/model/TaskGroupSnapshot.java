package com.openmanus.agentteam.domain.model;

/**
 * Aggregated view of one task group at a point in time.
 */
public record TaskGroupSnapshot(
        String groupId,
        int totalTasks,
        int pendingTasks,
        int claimedTasks,
        int runningTasks,
        int succeededTasks,
        int failedTasks,
        TaskGroupStatus status
) {

    public boolean allFinished() {
        return totalTasks > 0 && succeededTasks + failedTasks == totalTasks;
    }
}
