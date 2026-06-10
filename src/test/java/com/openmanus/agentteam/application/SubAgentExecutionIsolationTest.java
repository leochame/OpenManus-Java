package com.openmanus.agentteam.application;

import com.openmanus.agentteam.domain.model.SubTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubAgentExecutionIsolation Tests")
class SubAgentExecutionIsolationTest {

    @Test
    @DisplayName("should execute subtask through subagent role port")
    void shouldExecuteSubTaskThroughSubAgentRolePort() {
        RecordingRoleExecutionPort roleExecutionPort = new RecordingRoleExecutionPort("done");
        AgentTeamPromptProvider promptProvider = new AgentTeamPromptProvider() {
            @Override
            public String taskDecompositionPromptTemplate() {
                return "unused";
            }

            @Override
            public String teamMasterSystemPromptTemplate() {
                return "unused";
            }

            @Override
            public String subAgentSystemPromptTemplate() {
                return "unused";
            }

            @Override
            public String subAgentExecutionPromptTemplate() {
                return "task={{taskDescription}}\ncontext={{contextSummary}}";
            }
        };
        SubAgentExecutionService service = new SubAgentExecutionService(roleExecutionPort, promptProvider);
        SubTask subTask = new SubTask(
                "task-1",
                "group-1",
                "conv-1",
                "API",
                "Collect API requirements",
                "Parent request:\nBuild an API planning document",
                System.currentTimeMillis()
        );

        SubTaskExecutionOutput output = service.execute(subTask, "agent-1");

        assertThat(output.summary()).isEqualTo("done");
        assertThat(output.detail()).isEqualTo("done");
        assertThat(roleExecutionPort.context.role()).isEqualTo(AgentTeamRole.SUB_AGENT);
        assertThat(roleExecutionPort.context.parentSessionId()).isEqualTo("conv-1");
        assertThat(roleExecutionPort.context.groupId()).isEqualTo("group-1");
        assertThat(roleExecutionPort.context.taskId()).isEqualTo("task-1");
        assertThat(roleExecutionPort.context.agentId()).isEqualTo("agent-1");
        assertThat(roleExecutionPort.context.depth()).isEqualTo(1);
        assertThat(roleExecutionPort.input).contains("task=Collect API requirements");
        assertThat(roleExecutionPort.input).contains("context=Parent request:\nBuild an API planning document");
        assertThat(roleExecutionPort.context.memoryId()).isEqualTo("agentteam:conv-1:group-1:task-1:agent-1");
        assertThat(roleExecutionPort.context.memoryId()).isNotEqualTo("conv-1");
    }

    private static final class RecordingRoleExecutionPort implements AgentTeamRoleExecutionPort {

        private final String result;
        private AgentTeamExecutionContext context;
        private String input;

        private RecordingRoleExecutionPort(String result) {
            this.result = result;
        }

        @Override
        public String executeSync(AgentTeamExecutionContext context, String input) {
            this.context = context;
            this.input = input;
            return result;
        }
    }
}
