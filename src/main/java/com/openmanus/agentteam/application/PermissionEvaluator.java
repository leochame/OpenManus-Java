package com.openmanus.agentteam.application;

import com.openmanus.infra.config.AgentTeamProperties;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pluggable permission rule engine for agentteam host-mode execution.
 *
 * <p>Evaluates operations against a sorted list of {@link PermissionRule} entries.
 * Rules are loaded from {@link AgentTeamProperties.SecurityConfig#permissionRules} at construction time.
 *
 * <p>Matching algorithm:
 * <ol>
 *   <li>Load all rules, sorted by priority (ascending).</li>
 *   <li>For each rule, check if the tool name matches and the content matches the pattern.</li>
 *   <li>First match wins — return the rule's behavior.</li>
 *   <li>If no rule matches, return the configured default behavior.</li>
 * </ol>
 *
 * <p>Pattern syntax:
 * <ul>
 *   <li>Trailing {@code *} → prefix match (e.g. "git status*" matches "git status --porcelain")</li>
 *   <li>Glob patterns with {@code **}} → Java PathMatcher glob (e.g. "src/** /*.java")</li>
 *   <li>Plain text → exact match</li>
 * </ul>
 */
@Slf4j
public class PermissionEvaluator {

    private final List<PermissionRule> rules;
    private final PermissionBehavior defaultBehavior;

    public PermissionEvaluator(List<PermissionRule> rules, PermissionBehavior defaultBehavior) {
        this.rules = new ArrayList<>(Objects.requireNonNull(rules, "rules must not be null"));
        this.rules.sort(Comparator.comparingInt(PermissionRule::priority));
        this.defaultBehavior = Objects.requireNonNull(defaultBehavior, "defaultBehavior must not be null");
    }

    /**
     * Evaluate whether a tool operation is allowed.
     *
     * @param toolName the tool name (e.g. "bash", "file_read", "file_write")
     * @param content  the content to check (command string for bash, file path for file operations)
     * @return evaluation result
     */
    public PermissionCheckResult evaluate(String toolName, String content) {
        if (toolName == null || toolName.isBlank()) {
            return PermissionCheckResult.deny("toolName is blank", "VALIDATION:blank-tool");
        }
        if (content == null || content.isBlank()) {
            return defaultResult("content is blank — default", "VALIDATION:blank-content");
        }

        for (PermissionRule rule : rules) {
            if (!rule.toolName().equals(toolName)) {
                continue;
            }
            if (matches(content, rule.pattern())) {
                String reason = rule.behavior() == PermissionBehavior.ALLOW
                        ? "允许 (" + rule.description() + ")"
                        : "拒绝 (" + rule.description() + ")";
                log.debug("PermissionEvaluator matched: tool={} content={} rule={} behavior={}",
                        toolName, content, rule.pattern(), rule.behavior());
                return new PermissionCheckResult(rule.behavior(), reason, rule.pattern());
            }
        }

        return defaultResult("无匹配规则 — 默认" + defaultBehavior, "DEFAULT");
    }

    /**
     * Evaluate a file operation with path context.
     */
    public PermissionCheckResult evaluate(String toolName, String content, String worktreeRoot) {
        // For file operations, append worktree context info if provided
        PermissionCheckResult result = evaluate(toolName, content);
        if (!result.allowed() && worktreeRoot != null) {
            log.warn("PermissionEvaluator BLOCKED file operation: tool={} path={} worktreeRoot={} reason={}",
                    toolName, content, worktreeRoot, result.reason());
        }
        return result;
    }

    private boolean matches(String content, String pattern) {
        if (pattern.endsWith("*") && !pattern.contains("**") && !pattern.contains("?")) {
            // Prefix match
            String prefix = pattern.substring(0, pattern.length() - 1);
            return content.startsWith(prefix);
        }
        if (pattern.contains("*") || pattern.contains("?")) {
            // Glob match using PathMatcher
            try {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                return matcher.matches(Paths.get(content));
            } catch (Exception e) {
                // Fallback: treat as prefix match
                log.debug("PermissionEvaluator glob parse failed, falling back to prefix: pattern={} error={}",
                        pattern, e.getMessage());
                String effectivePrefix = pattern.replace("*", "");
                return content.startsWith(effectivePrefix);
            }
        }
        // Exact match
        return content.equals(pattern);
    }

    private PermissionCheckResult defaultResult(String reason, String rule) {
        if (defaultBehavior == PermissionBehavior.ALLOW) {
            return PermissionCheckResult.allow();
        }
        return PermissionCheckResult.deny(reason, rule);
    }

    public List<PermissionRule> rules() {
        return List.copyOf(rules);
    }

    public PermissionBehavior defaultBehavior() {
        return defaultBehavior;
    }
}
