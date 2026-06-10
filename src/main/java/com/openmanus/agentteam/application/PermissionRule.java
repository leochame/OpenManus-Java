package com.openmanus.agentteam.application;

/**
 * A single permission rule matching a tool + operation pattern.
 *
 * <p>Pattern format: {@code toolName:contentPattern}
 * <ul>
 *   <li>{@code bash:git status*} — prefix match (matches any command starting with "git status")</li>
 *   <li>{@code bash:rm -rf*} — prefix match</li>
 *   <li>{@code file:src/**}} — glob match on file path</li>
 * </ul>
 *
 * @param toolName    the tool this rule applies to ("bash", "file_read", "file_write")
 * @param pattern     the content pattern to match against
 * @param behavior    ALLOW or DENY
 * @param priority    lower number = higher priority
 * @param description human-readable explanation of the rule
 */
public record PermissionRule(
        String toolName,
        String pattern,
        PermissionBehavior behavior,
        int priority,
        String description
) {
    public PermissionRule {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("pattern must not be blank");
        }
        if (behavior == null) {
            throw new IllegalArgumentException("behavior must not be null");
        }
    }
}
