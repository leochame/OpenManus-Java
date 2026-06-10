package com.openmanus.agentteam.application;

/**
 * Thin facade for agent-team execution use cases.
 */
public class AgentTeamApplicationService {

    private final MasterAgentOrchestrator masterAgentOrchestrator;

    public AgentTeamApplicationService(MasterAgentOrchestrator masterAgentOrchestrator) {
        this.masterAgentOrchestrator = masterAgentOrchestrator;
    }

    public String execute(String userInput, String conversationId) {
        return masterAgentOrchestrator.execute(userInput, conversationId);
    }
}
