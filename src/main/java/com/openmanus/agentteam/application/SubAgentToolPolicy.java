package com.openmanus.agentteam.application;

import com.openmanus.aiframework.tool.AiRegisteredTool;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * First-version tool policy for sub-agents.
 */
public class SubAgentToolPolicy implements AgentTeamToolPolicy {

    static final Set<String> ALLOWED_TOOL_NAMES = Set.of(
            "browser_open_url",
            "browser_ensure_sandbox",
            "executePython",
            "executePythonFile",
            "search_web",
            "browser_fetch_web",
            "runShellCommand",
            "recordTask",
            "reflectOnTask",
            "getTaskHistory"
    );

    @Override
    public List<AiRegisteredTool> selectTools(List<AiRegisteredTool> defaultTools) {
        if (defaultTools == null || defaultTools.isEmpty()) {
            return List.of();
        }
        Set<String> deduplicatedNames = new LinkedHashSet<>();
        return defaultTools.stream()
                .filter(tool -> tool != null && ALLOWED_TOOL_NAMES.contains(tool.name()))
                .filter(tool -> deduplicatedNames.add(tool.name()))
                .toList();
    }
}
