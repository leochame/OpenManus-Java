package com.openmanus.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime configuration for the agentteam module.
 */
@Data
@ConfigurationProperties(prefix = "openmanus.agentteam")
public class AgentTeamProperties {

    private boolean enabled = true;
    private int workerCount = 3;
    private long idlePollIntervalMillis = 500L;
    private long masterPollIntervalMillis = 300L;
    private int maxSubTasksPerGroup = 5;

    @NestedConfigurationProperty
    private SecurityConfig security = new SecurityConfig();

    /**
     * Security configuration for agentteam host-mode execution.
     */
    @Data
    public static class SecurityConfig {
        /** Master switch — when false, all security checks are bypassed. */
        private boolean enabled = true;
        /** Commands starting with any of these prefixes are denied (after HARD_BLOCK check). */
        private List<String> denyCommands = new ArrayList<>();
        /** Commands starting with any of these prefixes bypass the deny list (but NOT HARD_BLOCK). */
        private List<String> allowCommands = new ArrayList<>();
        /** Extra paths that CODING_SUB_AGENT is allowed to access beyond the worktree root. */
        private List<String> allowedPaths = new ArrayList<>();
        /** Maximum length of a shell command. Commands exceeding this are rejected. */
        private int maxCommandLength = 4096;
        /** Permission rules used by PermissionEvaluator. */
        private List<PermissionRuleConfig> permissionRules = new ArrayList<>();
        /** Default behavior when no rule matches. ALLOW or DENY. */
        private String defaultBehavior = "DENY";
    }

    /**
     * A single permission rule entry loaded from YAML.
     */
    @Data
    public static class PermissionRuleConfig {
        private String pattern = "";
        private String behavior = "ALLOW";
        private int priority = 5;
        private String description = "";
    }
}
