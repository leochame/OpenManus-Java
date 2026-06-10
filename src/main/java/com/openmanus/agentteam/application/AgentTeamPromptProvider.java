package com.openmanus.agentteam.application;

/**
 * Provides prompt templates used by the agentteam module.
 */
public interface AgentTeamPromptProvider {

    String taskDecompositionPromptTemplate();

    String teamMasterSystemPromptTemplate();

    String subAgentSystemPromptTemplate();

    String subAgentExecutionPromptTemplate();
}
