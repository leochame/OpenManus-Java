package com.openmanus.agentteam.application;

/**
 * Behaviors for permission evaluation.
 */
public enum PermissionBehavior {
    /** Explicitly allow the operation. */
    ALLOW,
    /** Explicitly deny the operation — the AI can read the rejection reason and try an alternative. */
    DENY
}
