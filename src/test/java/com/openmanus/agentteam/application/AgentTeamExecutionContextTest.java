package com.openmanus.agentteam.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentTeamExecutionContext Tests")
class AgentTeamExecutionContextTest {

    @Test
    @DisplayName("should build subagent execution context with stable runtime identity")
    void shouldBuildSubAgentExecutionContextWithStableRuntimeIdentity() {
        AgentTeamExecutionContext context = AgentTeamExecutionContext.subAgent(
                "conv-1",
                "group-1",
                "task-1",
                "agent-1"
        );

        assertThat(context.role()).isEqualTo(AgentTeamRole.SUB_AGENT);
        assertThat(context.parentSessionId()).isEqualTo("conv-1");
        assertThat(context.groupId()).isEqualTo("group-1");
        assertThat(context.taskId()).isEqualTo("task-1");
        assertThat(context.agentId()).isEqualTo("agent-1");
        assertThat(context.depth()).isEqualTo(1);
        assertThat(context.memoryId()).isEqualTo("agentteam:conv-1:group-1:task-1:agent-1");
    }
}
