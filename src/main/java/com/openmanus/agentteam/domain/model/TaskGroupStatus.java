package com.openmanus.agentteam.domain.model;

/**
 * Lifecycle for one decomposed task group.
 */
public enum TaskGroupStatus {
    CREATED,
    RUNNING,
    COMPLETED,
    FAILED,
    PARTIAL_FAILED
}
