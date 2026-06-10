package com.openmanus.agentteam.application;

/**
 * Result of a permission evaluation.
 *
 * @param behavior ALLOW or DENY
 * @param reason   human-readable reason (shown to AI so it can adapt)
 * @param rule     the matched rule description, or "DEFAULT" if no rule matched
 */
public record PermissionCheckResult(PermissionBehavior behavior, String reason, String rule) {

    public static PermissionCheckResult allow() {
        return new PermissionCheckResult(PermissionBehavior.ALLOW, "No matching deny rule — default allow", "DEFAULT");
    }

    public static PermissionCheckResult deny(String reason, String rule) {
        return new PermissionCheckResult(PermissionBehavior.DENY, reason, rule);
    }

    public boolean allowed() {
        return behavior == PermissionBehavior.ALLOW;
    }
}
