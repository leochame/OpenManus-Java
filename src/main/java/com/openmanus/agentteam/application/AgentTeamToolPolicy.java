package com.openmanus.agentteam.application;

import com.openmanus.aiframework.tool.AiRegisteredTool;

import java.util.List;

/**
 * Selects the local tool set available to one agentteam role.
 */
public interface AgentTeamToolPolicy {

    List<AiRegisteredTool> selectTools(List<AiRegisteredTool> defaultTools);
}
