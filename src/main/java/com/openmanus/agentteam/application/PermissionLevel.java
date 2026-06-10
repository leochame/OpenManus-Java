package com.openmanus.agentteam.application;

/**
 * Permission level controlling what an agent instance is allowed to do.
 *
 * <p>Modeled after Claude Code's Agent Permission Mode:
 * <ul>
 *   <li>{@link #FULL} — no security checks; already inside a Docker sandbox.</li>
 *   <li>{@link #SANDBOXED} — Docker sandbox provides physical isolation; no extra rules needed.</li>
 *   <li>{@link #RESTRICTED} — executes on Host OS; must pass BashSecurityChecker + PermissionEvaluator.</li>
 *   <li>{@link #READ_ONLY} — future use for Plan/Review agents; read-only filesystem access, no shell exec.</li>
 * </ul>
 */
public enum PermissionLevel {
    /** Fully trusted — no checks needed (TEAM_MASTER in Docker sandbox). */
    FULL,
    /** Sandboxed — Docker provides physical isolation (SUB_AGENT in Docker sandbox). */
    SANDBOXED,
    /** Restricted — Host OS execution with full security checks (CODING_SUB_AGENT). */
    RESTRICTED,
    /** Read-only — future Plan agent; file reads and search only, no writes, no shell exec. */
    READ_ONLY
}
