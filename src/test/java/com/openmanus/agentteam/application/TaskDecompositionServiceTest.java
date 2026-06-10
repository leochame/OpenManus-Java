package com.openmanus.agentteam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.agentteam.domain.model.DecompositionPlan;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("TaskDecompositionService Tests")
class TaskDecompositionServiceTest {

    private final AgentTeamPromptProvider promptProvider = new AgentTeamPromptProvider() {
        @Override
        public String taskDecompositionPromptTemplate() {
            return "Decompose this request into parallel subtasks:\n{{userInput}}\nlimit={{maxSubTasks}}";
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
            return "unused";
        }
    };

    @Test
    @DisplayName("should accept structured independent subtasks from llm")
    void shouldAcceptStructuredIndependentSubTasksFromLlm() {
        AiChatModel aiChatModel = mock(AiChatModel.class);
        when(aiChatModel.chat(any())).thenReturn(new AiChatResponse(
                AiChatMessage.assistant("""
                        {
                          "parallelizable": true,
                          "reason": "Independent work items",
                          "subTasks": [
                            {"title": "API requirements", "description": "Collect API requirements"},
                            {"title": "Deployment risks", "description": "Summarize deployment risks"},
                            {"title": "Checklist", "description": "Write a short validation checklist"}
                          ]
                        }
                        """),
                null,
                null,
                null,
                null,
                null
        ));
        TaskDecompositionService service =
                new TaskDecompositionService(aiChatModel, new ObjectMapper(), promptProvider);

        DecompositionPlan plan = service.decompose("big task", 5);

        assertThat(plan.parallelizable()).isTrue();
        assertThat(plan.reason()).isEqualTo("Independent work items");
        assertThat(plan.subTasks()).hasSize(3);
        assertThat(plan.subTasks())
                .extracting(item -> item.description())
                .containsExactly(
                        "Collect API requirements",
                        "Summarize deployment risks",
                        "Write a short validation checklist"
                );
        assertThat(plan.subTasks())
                .extracting(item -> item.contextSummary())
                .allSatisfy(summary -> assertThat(summary).contains("Parent request:", "Team instruction:"));
    }

    @Test
    @DisplayName("should reject tasks with obvious dependency hints from llm result")
    void shouldRejectTasksWithObviousDependencyHintsFromLlmResult() {
        AiChatModel aiChatModel = mock(AiChatModel.class);
        when(aiChatModel.chat(any())).thenReturn(new AiChatResponse(
                AiChatMessage.assistant("""
                        {
                          "parallelizable": true,
                          "reason": "Proposed by model",
                          "subTasks": [
                            {"title": "Analysis", "description": "Collect API requirements"},
                            {"title": "Implementation", "description": "Then implement the service based on the approved requirements"}
                          ]
                        }
                        """),
                null,
                null,
                null,
                null,
                null
        ));
        TaskDecompositionService service =
                new TaskDecompositionService(aiChatModel, new ObjectMapper(), promptProvider);

        DecompositionPlan plan = service.decompose("big task", 5);

        assertThat(plan.parallelizable()).isFalse();
        assertThat(plan.reason()).isNotBlank();
    }

    @Test
    @DisplayName("should fall back to rule decomposition when llm fails")
    void shouldFallBackToRuleDecompositionWhenLlmFails() {
        AiChatModel aiChatModel = mock(AiChatModel.class);
        when(aiChatModel.chat(any())).thenThrow(new IllegalStateException("boom"));
        TaskDecompositionService service =
                new TaskDecompositionService(aiChatModel, new ObjectMapper(), promptProvider);

        DecompositionPlan plan = service.decompose("""
                - Task 1
                - Task 2
                - Task 3
                """, 5);

        assertThat(plan.parallelizable()).isTrue();
        assertThat(plan.subTasks()).hasSize(3);
        assertThat(plan.subTasks())
                .extracting(item -> item.contextSummary())
                .allSatisfy(summary -> assertThat(summary).contains("Parent request:", "Team instruction:"));
    }

    @Test
    @DisplayName("should reject inputs with fewer than two subtasks after fallback")
    void shouldRejectInputsWithFewerThanTwoSubTasksAfterFallback() {
        AiChatModel aiChatModel = mock(AiChatModel.class);
        when(aiChatModel.chat(any())).thenThrow(new IllegalStateException("boom"));
        TaskDecompositionService service =
                new TaskDecompositionService(aiChatModel, new ObjectMapper(), promptProvider);

        DecompositionPlan plan = service.decompose("- Only one task", 5);

        assertThat(plan.parallelizable()).isFalse();
        assertThat(plan.subTasks()).hasSize(1);
    }

    @Test
    @DisplayName("should respect max subtasks limit")
    void shouldRespectMaxSubTasksLimit() {
        AiChatModel aiChatModel = mock(AiChatModel.class);
        when(aiChatModel.chat(any())).thenReturn(new AiChatResponse(
                AiChatMessage.assistant("""
                        {
                          "parallelizable": true,
                          "reason": "Independent work items",
                          "subTasks": [
                            {"title": "Task 1", "description": "Task 1"},
                            {"title": "Task 2", "description": "Task 2"},
                            {"title": "Task 3", "description": "Task 3"},
                            {"title": "Task 4", "description": "Task 4"}
                          ]
                        }
                        """),
                null,
                null,
                null,
                null,
                null
        ));
        TaskDecompositionService service =
                new TaskDecompositionService(aiChatModel, new ObjectMapper(), promptProvider);

        DecompositionPlan plan = service.decompose("big task", 2);

        assertThat(plan.parallelizable()).isTrue();
        assertThat(plan.subTasks()).hasSize(2);
    }

    @Test
    @DisplayName("should preserve llm context summary when provided")
    void shouldPreserveLlmContextSummaryWhenProvided() {
        AiChatModel aiChatModel = mock(AiChatModel.class);
        when(aiChatModel.chat(any())).thenReturn(new AiChatResponse(
                AiChatMessage.assistant("""
                        {
                          "parallelizable": true,
                          "reason": "Independent work items",
                          "subTasks": [
                            {
                              "title": "API requirements",
                              "description": "Collect API requirements",
                              "contextSummary": "Parent request: Build a service\\nFocus: API contract only"
                            },
                            {
                              "title": "Deployment risks",
                              "description": "Summarize deployment risks",
                              "contextSummary": "Parent request: Build a service\\nFocus: rollout risk only"
                            }
                          ]
                        }
                        """),
                null,
                null,
                null,
                null,
                null
        ));
        TaskDecompositionService service =
                new TaskDecompositionService(aiChatModel, new ObjectMapper(), promptProvider);

        DecompositionPlan plan = service.decompose("Build a service", 5);

        assertThat(plan.parallelizable()).isTrue();
        assertThat(plan.subTasks())
                .extracting(item -> item.contextSummary())
                .containsExactly(
                        "Parent request: Build a service\nFocus: API contract only",
                        "Parent request: Build a service\nFocus: rollout risk only"
                );
    }
}
