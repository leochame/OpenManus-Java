package com.openmanus.java.model;

/**
 * Agent execution states, corresponding to app/schema.py's AgentState.
 */
public enum AgentState {
    IDLE,
    RUNNING,
    FINISHED,
    ERROR
}