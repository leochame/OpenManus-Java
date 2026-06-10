package com.openmanus.agentteam.domain.model;

/**
 * Failed subtask result prepared for final aggregation.
 */
public record SubTaskFailure(
        String taskId,
        String title,
        String errorMessage
) {
}
