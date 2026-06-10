package com.openmanus.agentteam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.agentteam.domain.service.DefaultResultAggregationService;
import com.openmanus.agentteam.domain.service.DefaultTaskGroupManager;
import com.openmanus.agentteam.domain.service.TaskGroupStatusCalculator;
import com.openmanus.agentteam.infra.InMemoryAgentMessageBus;
import com.openmanus.agentteam.infra.InMemoryTaskGroupRepository;
import com.openmanus.agentteam.infra.InMemoryTaskPool;
import com.openmanus.agentteam.infra.SubAgentWorkerManager;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatResponse;
import com.openmanus.domain.service.AgentExecutionPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MasterAgentOrchestrator Tests")
class MasterAgentOrchestratorTest {

    private final AgentTeamPromptProvider promptProvider = new AgentTeamPromptProvider() {
        @Override
        public String taskDecompositionPromptTemplate() {
            return "Decompose this request into parallel subtasks:\n{{userInput}}\nlimit={{maxSubTasks}}";
        }

        @Override
        public String teamMasterSystemPromptTemplate() {
            return "You are the team master";
        }

        @Override
        public String subAgentSystemPromptTemplate() {
            return "You are the subagent";
        }

        @Override
        public String subAgentExecutionPromptTemplate() {
            return """
                    agentId={{agentId}}
                    taskId={{taskId}}
                    groupId={{groupId}}
                    title={{taskTitle}}
                    description={{taskDescription}}
                    contextSummary={{contextSummary}}
                    """;
        }
    };

    private SubAgentExecutionService subAgentExecutionService(AgentExecutionPort agentExecutionPort) {
        AgentTeamRoleExecutionPort roleExecutionPort = (context, input) ->
                agentExecutionPort.executeSync(input, context.memoryId());
        return new SubAgentExecutionService(roleExecutionPort, promptProvider);
    }

    @Test
    @DisplayName("should fall back to single agent when request is not safely parallelizable")
    void shouldFallBackToSingleAgentWhenRequestIsNotSafelyParallelizable() {
        AgentExecutionPort agentExecutionPort = mock(AgentExecutionPort.class);
        when(agentExecutionPort.executeSync("Implement this feature end to end", "conv-1"))
                .thenReturn("single-agent-result");
        AiChatModel aiChatModel = mock(AiChatModel.class);
        when(aiChatModel.chat(any())).thenThrow(new IllegalStateException("fallback to rule"));

        InMemoryTaskGroupRepository repository = new InMemoryTaskGroupRepository();
        InMemoryTaskPool taskPool = new InMemoryTaskPool(repository);
        SubAgentWorkerManager workerManager = new SubAgentWorkerManager(
                2,
                10L,
                taskPool,
                new InMemoryAgentMessageBus(),
                subAgentExecutionService(agentExecutionPort)
        );

        try {
            MasterAgentOrchestrator orchestrator = new MasterAgentOrchestrator(
                    agentExecutionPort,
                    new TaskDecompositionService(aiChatModel, new ObjectMapper(), promptProvider),
                    new DefaultTaskGroupManager(repository, new TaskGroupStatusCalculator()),
                    taskPool,
                    new DefaultResultAggregationService(),
                    workerManager,
                    10L,
                    5
            );

            String result = orchestrator.execute("Implement this feature end to end", "conv-1");

            assertThat(result).isEqualTo("single-agent-result");
            assertThat(workerManager.getWorkerIds()).isEmpty();
            verify(agentExecutionPort).executeSync("Implement this feature end to end", "conv-1");
        } finally {
            workerManager.close();
        }
    }

    @Test
    @DisplayName("should execute parallel subtasks through shared agent execution port")
    void shouldExecuteParallelSubTasksThroughSharedAgentExecutionPort() {
        AgentExecutionPort agentExecutionPort = mock(AgentExecutionPort.class);
        when(agentExecutionPort.execute(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture("unused"));
        when(agentExecutionPort.executeSync(anyString(), anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0, String.class);
            String conversationId = invocation.getArgument(1, String.class);
            if (prompt.contains("Collect API requirements")) {
                return "Collected requirements for " + conversationId;
            }
            if (prompt.contains("Summarize deployment risks")) {
                return "Summarized risks for " + conversationId;
            }
            return "Generic result for " + conversationId;
        });
        AiChatModel aiChatModel = mock(AiChatModel.class);
        when(aiChatModel.chat(any())).thenReturn(new AiChatResponse(
                AiChatMessage.assistant("""
                        {
                          "parallelizable": true,
                          "reason": "Independent work items",
                          "subTasks": [
                            {"title": "API requirements", "description": "Collect API requirements", "contextSummary": "Parent request: Please decompose this request\\nFocus: gather API needs"},
                            {"title": "Deployment risks", "description": "Summarize deployment risks", "contextSummary": "Parent request: Please decompose this request\\nFocus: identify rollout risks"}
                          ]
                        }
                        """),
                null,
                null,
                null,
                null,
                null
        ));

        InMemoryTaskGroupRepository repository = new InMemoryTaskGroupRepository();
        InMemoryTaskPool taskPool = new InMemoryTaskPool(repository);
        SubAgentWorkerManager workerManager = new SubAgentWorkerManager(
                2,
                10L,
                taskPool,
                new InMemoryAgentMessageBus(),
                subAgentExecutionService(agentExecutionPort)
        );

        try {
            MasterAgentOrchestrator orchestrator = new MasterAgentOrchestrator(
                    agentExecutionPort,
                    new TaskDecompositionService(aiChatModel, new ObjectMapper(), promptProvider),
                    new DefaultTaskGroupManager(repository, new TaskGroupStatusCalculator()),
                    taskPool,
                    new DefaultResultAggregationService(),
                    workerManager,
                    10L,
                    5
            );

            String result = orchestrator.execute("Please decompose this request", "conv-2");

            assertThat(workerManager.getWorkerIds()).hasSize(2);
            assertThat(result).contains("status: COMPLETED");
            assertThat(result).contains("success: 2");
            assertThat(result).contains("failed: 0");
            assertThat(result).contains("API requirements");
            assertThat(result).contains("Deployment risks");
            verify(agentExecutionPort, atLeast(1))
                    .executeSync(org.mockito.ArgumentMatchers.contains("contextSummary=Parent request: Please decompose this request"), anyString());
            verify(agentExecutionPort, never()).executeSync(eq("Please decompose this request"), eq("conv-2"));
            verify(agentExecutionPort, atLeast(2)).executeSync(anyString(), anyString());
        } finally {
            workerManager.close();
        }
    }

    @Test
    @DisplayName("should report partial failure when one subtask execution fails")
    void shouldReportPartialFailureWhenOneSubTaskExecutionFails() {
        AgentExecutionPort agentExecutionPort = mock(AgentExecutionPort.class);
        when(agentExecutionPort.execute(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture("unused"));
        when(agentExecutionPort.executeSync(anyString(), anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0, String.class);
            String conversationId = invocation.getArgument(1, String.class);
            if (prompt.contains("Collect API requirements")) {
                return "Collected requirements for " + conversationId;
            }
            if (prompt.contains("Summarize deployment risks")) {
                throw new IllegalStateException("subtask failed intentionally");
            }
            return "Generic result for " + conversationId;
        });
        AiChatModel aiChatModel = mock(AiChatModel.class);
        when(aiChatModel.chat(any())).thenReturn(new AiChatResponse(
                AiChatMessage.assistant("""
                        {
                          "parallelizable": true,
                          "reason": "Independent work items",
                          "subTasks": [
                            {"title": "API requirements", "description": "Collect API requirements", "contextSummary": "Parent request: Please decompose this request\\nFocus: gather API needs"},
                            {"title": "Deployment risks", "description": "Summarize deployment risks", "contextSummary": "Parent request: Please decompose this request\\nFocus: identify rollout risks"}
                          ]
                        }
                        """),
                null,
                null,
                null,
                null,
                null
        ));

        InMemoryTaskGroupRepository repository = new InMemoryTaskGroupRepository();
        InMemoryTaskPool taskPool = new InMemoryTaskPool(repository);
        SubAgentWorkerManager workerManager = new SubAgentWorkerManager(
                2,
                10L,
                taskPool,
                new InMemoryAgentMessageBus(),
                subAgentExecutionService(agentExecutionPort)
        );

        try {
            MasterAgentOrchestrator orchestrator = new MasterAgentOrchestrator(
                    agentExecutionPort,
                    new TaskDecompositionService(aiChatModel, new ObjectMapper(), promptProvider),
                    new DefaultTaskGroupManager(repository, new TaskGroupStatusCalculator()),
                    taskPool,
                    new DefaultResultAggregationService(),
                    workerManager,
                    10L,
                    5
            );

            String result = orchestrator.execute("Please decompose this request", "conv-3");

            assertThat(result).contains("status: PARTIAL_FAILED");
            assertThat(result).contains("success: 1");
            assertThat(result).contains("failed: 1");
            assertThat(result).contains("Failed subtasks:");
            assertThat(result).contains("subtask failed intentionally");
        } finally {
            workerManager.close();
        }
    }
}
