package com.openmanus.agentteam.infra;

import com.openmanus.infra.config.AgentTeamProperties;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Hardcoded dangerous-command detector for CODING_SUB_AGENT host-mode execution.
 *
 * <p>Design:
 * <ol>
 *   <li>HARD_BLOCK patterns (regex) — these are NEVER allowed, even if config would permit them.</li>
 *   <li>Configurable DENY_LIST prefixes — loaded from {@link AgentTeamProperties.SecurityConfig#denyCommands}.</li>
 *   <li>Configurable ALLOW_LIST prefixes — loaded from {@link AgentTeamProperties.SecurityConfig#allowCommands};
 *       these bypass the DENY_LIST check but <em>not</em> HARD_BLOCK.</li>
 *   <li>File path boundary check — commands must not reference paths outside the worktree
 *       or configured allowed-paths.</li>
 * </ol>
 *
 * <p>Thread-safety: stateless — all patterns are pre-compiled at construction time.
 */
@Slf4j
public class BashSecurityChecker {

    private final boolean enabled;
    private final int maxCommandLength;
    private final List<String> denyPrefixes;
    private final List<String> allowPrefixes;
    private final List<String> allowedPaths;

    /**
     * Hard-block patterns — these commands are dangerous under any circumstances.
     * Ordered by severity. Each entry has a pattern and a human-readable rule name.
     */
    private static final Map<Pattern, String> HARD_BLOCK_PATTERNS = new LinkedHashMap<>();

    static {
        // Recursive force delete of root / home
        HARD_BLOCK_PATTERNS.put(
                Pattern.compile("\\brm\\s+(-[a-zA-Z]*[rRf][a-zA-Z]*\\s+)*(-[a-zA-Z]*[rRf][a-zA-Z]*\\s+)?[/~]"),
                "HARD_BLOCK:rm-root");
        HARD_BLOCK_PATTERNS.put(
                Pattern.compile("\\brm\\s+(-[a-zA-Z]*[rRf][a-zA-Z]*\\s+)*(-[a-zA-Z]*[rRf][a-zA-Z]*\\s+)?/\\*"),
                "HARD_BLOCK:rm-root-star");
        HARD_BLOCK_PATTERNS.put(
                Pattern.compile("\\brm\\s+(-[a-zA-Z]*[rRf][a-zA-Z]*\\s+)*-rf\\s+~"),
                "HARD_BLOCK:rm-home");

        // curl / wget piped to shell
        HARD_BLOCK_PATTERNS.put(
                Pattern.compile("\\b(curl|wget)\\b.*\\|\\s*(ba)?sh\\b"),
                "HARD_BLOCK:curl-pipe-shell");
        HARD_BLOCK_PATTERNS.put(
                Pattern.compile("\\b(curl|wget)\\b.*\\|\\s*(ba)?sh\\b"),
                "HARD_BLOCK:wget-pipe-shell");

        // chmod 777 on root / recursive
        HARD_BLOCK_PATTERNS.put(
                Pattern.compile("\\bchmod\\s+(-[a-zA-Z]*[Rr][a-zA-Z]*\\s+)?777\\b"),
                "HARD_BLOCK:chmod-777");

        // Direct disk writes
        HARD_BLOCK_PATTERNS.put(
                Pattern.compile("\\bdd\\s+if="),
                "HARD_BLOCK:dd-if");

        // Fork bomb
        HARD_BLOCK_PATTERNS.put(
                Pattern.compile(":\\(\\)\\s*\\{\\s*:\\s*\\|\\s*:&\\s*\\}\\s*;\\s*:"),
                "HARD_BLOCK:fork-bomb");

        // eval / exec wrapping suspicious content
        HARD_BLOCK_PATTERNS.put(
                Pattern.compile("\\beval\\s+.*\\b(curl|wget|rm\\s+-rf|chmod\\s+777)\\b"),
                "HARD_BLOCK:eval-dangerous");
        HARD_BLOCK_PATTERNS.put(
                Pattern.compile("\\bexec\\s+.*\\b(curl|wget|rm\\s+-rf|chmod\\s+777)\\b"),
                "HARD_BLOCK:exec-dangerous");

        // git push --force to protected branches
        HARD_BLOCK_PATTERNS.put(
                Pattern.compile("\\bgit\\s+push\\s+(-[a-zA-Z]*[fF][a-zA-Z]*\\s+)*origin\\s+(main|master)\\b"),
                "HARD_BLOCK:git-force-push-main");
        HARD_BLOCK_PATTERNS.put(
                Pattern.compile("\\bgit\\s+push\\s+--force\\b"),
                "HARD_BLOCK:git-force-push");

        // Delete git branches forcefully
        HARD_BLOCK_PATTERNS.put(
                Pattern.compile("\\bgit\\s+branch\\s+-D\\s+(main|master)\\b"),
                "HARD_BLOCK:git-branch-delete-main");

        // Overwrite critical system files
        HARD_BLOCK_PATTERNS.put(
                Pattern.compile("\\b>(>)?\\s*/dev/sd[a-z]"),
                "HARD_BLOCK:overwrite-dev");
        HARD_BLOCK_PATTERNS.put(
                Pattern.compile("\\b>(>)?\\s*/etc/"),
                "HARD_BLOCK:overwrite-etc");

        // Network listeners on privileged ports
        HARD_BLOCK_PATTERNS.put(
                Pattern.compile("\\bnc\\s+-[lL]"),
                "HARD_BLOCK:nc-listener");
    }

    public BashSecurityChecker(AgentTeamProperties.SecurityConfig securityConfig) {
        Objects.requireNonNull(securityConfig, "securityConfig must not be null");
        this.enabled = securityConfig.isEnabled();
        this.maxCommandLength = securityConfig.getMaxCommandLength() > 0
                ? securityConfig.getMaxCommandLength()
                : 4096;
        this.denyPrefixes = List.copyOf(
                securityConfig.getDenyCommands() == null ? List.of() : securityConfig.getDenyCommands());
        this.allowPrefixes = List.copyOf(
                securityConfig.getAllowCommands() == null ? List.of() : securityConfig.getAllowCommands());
        this.allowedPaths = List.copyOf(
                securityConfig.getAllowedPaths() == null ? List.of() : securityConfig.getAllowedPaths());
    }

    /**
     * Convenience constructor for tests.
     */
    public BashSecurityChecker(boolean enabled, List<String> denyCommands, List<String> allowCommands) {
        this.enabled = enabled;
        this.maxCommandLength = 4096;
        this.denyPrefixes = List.copyOf(denyCommands == null ? List.of() : denyCommands);
        this.allowPrefixes = List.copyOf(allowCommands == null ? List.of() : allowCommands);
        this.allowedPaths = List.of();
    }

    /**
     * Check whether a bash command is safe to execute.
     *
     * @param command   the shell command to check
     * @param cwd       the working directory for path-boundary checks (nullable)
     * @param sessionId session identifier for logging (nullable)
     * @return check result indicating allow/deny and reason
     */
    public BashSecurityCheckResult check(String command, String cwd, String sessionId) {
        if (!enabled) {
            return BashSecurityCheckResult.allow();
        }

        if (command == null || command.isBlank()) {
            return BashSecurityCheckResult.deny("命令为空", "VALIDATION:empty-command");
        }

        if (command.length() > maxCommandLength) {
            return BashSecurityCheckResult.deny(
                    "命令长度超过限制 (" + command.length() + " > " + maxCommandLength + ")",
                    "VALIDATION:command-too-long");
        }

        // --- Layer 1: Hard block patterns (always enforced) ---
        for (Map.Entry<Pattern, String> entry : HARD_BLOCK_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(command).find()) {
                String rule = entry.getValue();
                String reason = "安全策略拒绝 (" + rule + "): 命令包含禁止的危险操作";
                log.warn("BashSecurityChecker HARD_BLOCK sessionId={} rule={} command={}", sessionId, rule, command);
                return BashSecurityCheckResult.deny(reason, rule);
            }
        }

        // --- Layer 2: Configurable allow list (checked first — bypasses deny list) ---
        for (String allowPrefix : allowPrefixes) {
            if (command.startsWith(allowPrefix)) {
                log.debug("BashSecurityChecker ALLOW sessionId={} prefix={} command={}", sessionId, allowPrefix, command);
                return BashSecurityCheckResult.allow();
            }
        }

        // --- Layer 3: Configurable deny list ---
        for (String denyPrefix : denyPrefixes) {
            if (command.startsWith(denyPrefix)) {
                String rule = "DENY_LIST:" + denyPrefix;
                String reason = "安全策略拒绝 (" + rule + "): 命令前缀在禁用列表中";
                log.warn("BashSecurityChecker DENY_LIST sessionId={} prefix={} command={}", sessionId, denyPrefix, command);
                return BashSecurityCheckResult.deny(reason, rule);
            }
        }

        // --- Layer 4: Path boundary check ---
        if (cwd != null && !cwd.isBlank()) {
            BashSecurityCheckResult pathCheck = checkPathBoundary(command, cwd);
            if (!pathCheck.allowed()) {
                return pathCheck;
            }
        }

        return BashSecurityCheckResult.allow();
    }

    /**
     * Detect if the command references paths outside the worktree.
     */
    private BashSecurityCheckResult checkPathBoundary(String command, String cwd) {
        Path worktreeRoot = Paths.get(cwd).toAbsolutePath().normalize();

        // Extract candidate paths from the command (naive regex: space-delimited tokens that look like paths)
        String[] tokens = command.split("\\s+");
        for (String token : tokens) {
            // Skip flags, options, and redirect operators
            if (token.startsWith("-") || token.equals("|") || token.equals(">") || token.equals("<")
                    || token.equals(">>") || token.equals("&&") || token.equals("||") || token.equals(";")) {
                continue;
            }
            // Check for ../ path traversal
            if (token.contains("../") || token.contains("..\\")) {
                Path candidate = worktreeRoot.resolve(token).normalize();
                if (!candidate.startsWith(worktreeRoot)) {
                    log.warn("BashSecurityChecker PATH_TRAVERSAL command={} token={} worktreeRoot={}",
                            command, token, worktreeRoot);
                    return BashSecurityCheckResult.deny(
                            "安全策略拒绝 (PATH_TRAVERSAL): 命令试图访问工作树之外的路径: " + token,
                            "PATH_TRAVERSAL:" + token);
                }
            }
        }
        return BashSecurityCheckResult.allow();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int maxCommandLength() {
        return maxCommandLength;
    }
}
