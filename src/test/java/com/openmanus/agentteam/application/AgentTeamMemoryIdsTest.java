package com.openmanus.agentteam.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentTeamMemoryIds Tests")
class AgentTeamMemoryIdsTest {

    @Test
    @DisplayName("should build stable subagent memory id")
    void shouldBuildStableSubAgentMemoryId() {
        String memoryId = AgentTeamMemoryIds.subAgent("conv-1", "group-1", "task-1", "agent-1");

        assertThat(memoryId).isEqualTo("agentteam:conv-1:group-1:task-1:agent-1");
        assertThat(memoryId).startsWith("agentteam:");
        assertThat(memoryId).contains("group-1", "task-1", "agent-1");
    }

    @Test
    @DisplayName("should use placeholders for blank segments")
    void shouldUsePlaceholdersForBlankSegments() {
        String memoryId = AgentTeamMemoryIds.subAgent(" ", null, "task-1", "");

        assertThat(memoryId).isEqualTo("agentteam:_:_:task-1:_");
    }
}
