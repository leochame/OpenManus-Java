package com.openmanus.agentteam.infra;

/**
 * Result of a bash security check.
 *
 * @param allowed whether the command is allowed to execute
 * @param reason  human-readable reason for the decision (shown to AI so it can adapt)
 * @param rule    the name of the rule that matched, or "DEFAULT" if no rule matched
 */
public record BashSecurityCheckResult(boolean allowed, String reason, String rule) {

    public static BashSecurityCheckResult allow() {
        return new BashSecurityCheckResult(true, "命令通过安全检查", "DEFAULT");
    }

    public static BashSecurityCheckResult deny(String reason, String rule) {
        return new BashSecurityCheckResult(false, reason, rule);
    }
}
