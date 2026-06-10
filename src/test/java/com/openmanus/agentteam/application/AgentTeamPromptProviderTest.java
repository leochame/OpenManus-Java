package com.openmanus.agentteam.application;

import com.openmanus.agentteam.infra.ClasspathAgentTeamPromptProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentTeamPromptProvider Tests")
class AgentTeamPromptProviderTest {

    private final AgentTeamPromptProvider promptProvider = new ClasspathAgentTeamPromptProvider();

    @Test
    @DisplayName("should load all agentteam prompt templates")
    void shouldLoadAllAgentTeamPromptTemplates() {
        assertThat(promptProvider.taskDecompositionPromptTemplate()).isNotBlank();
        assertThat(promptProvider.teamMasterSystemPromptTemplate()).contains("Team Master");
        assertThat(promptProvider.subAgentSystemPromptTemplate()).contains("SubAgent");
        assertThat(promptProvider.subAgentExecutionPromptTemplate()).contains("subtask");
    }
}
