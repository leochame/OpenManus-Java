package com.openmanus.agentteam.application;

import com.openmanus.aiframework.tool.AiRegisteredTool;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * First-version tool policy for the team master role.
 */
public class TeamMasterToolPolicy implements AgentTeamToolPolicy {

    static final Set<String> ALLOWED_TOOL_NAMES = Set.of(
            "search_web",
            "browser_fetch_web",
            "runShellCommand"
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
