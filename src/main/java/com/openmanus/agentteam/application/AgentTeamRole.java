package com.openmanus.agentteam.application;

/**
 * Roles inside the agentteam execution flow.
 *
 * <p>Each role has an associated {@link PermissionLevel} that determines what
 * security checks are applied:
 * <ul>
 *   <li>{@link #TEAM_MASTER} → {@link PermissionLevel#FULL} (Docker sandbox)</li>
 *   <li>{@link #SUB_AGENT} → {@link PermissionLevel#SANDBOXED} (Docker sandbox)</li>
 *   <li>{@link #CODING_SUB_AGENT} → {@link PermissionLevel#RESTRICTED} (Host OS + security checks)</li>
 * </ul>
 */
public enum AgentTeamRole {
    TEAM_MASTER(PermissionLevel.FULL),
    SUB_AGENT(PermissionLevel.SANDBOXED),
    /** Sub-agent that executes directly on Host OS (no Docker sandbox), for coding worktree access. */
    CODING_SUB_AGENT(PermissionLevel.RESTRICTED);

    private final PermissionLevel permissionLevel;

    AgentTeamRole(PermissionLevel permissionLevel) {
        this.permissionLevel = permissionLevel;
    }

    public PermissionLevel permissionLevel() {
        return permissionLevel;
    }
}
