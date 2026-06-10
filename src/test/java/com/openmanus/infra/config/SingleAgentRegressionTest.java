package com.openmanus.infra.config;

import com.openmanus.agent.coordination.AgentCoordinator;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiMemory;
import com.openmanus.aiframework.runtime.AiMemoryProvider;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatRequest;
import com.openmanus.aiframework.runtime.model.AiChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SingleAgentRegression Tests")
class SingleAgentRegressionTest {

    @Test
    @DisplayName("default single-agent coordinator should still use the built-in system prompt")
    void defaultSingleAgentCoordinatorShouldStillUseBuiltInSystemPrompt() {
        CapturingChatModel chatModel = new CapturingChatModel("single-agent-result");
        AgentCoordinator coordinator = AgentCoordinator.builder()
                .aiChatModel(chatModel)
                .aiMemoryProvider(new InMemoryProvider())
                .build();

        String result = coordinator.execute("hello", "memory-1");

        assertThat(result).isEqualTo("single-agent-result");
        assertThat(chatModel.lastRequest()).isNotNull();
        assertThat(chatModel.lastRequest().messages()).isNotEmpty();
        assertThat(chatModel.lastRequest().messages().getFirst().role()).isEqualTo(AiChatMessage.Role.SYSTEM);
        assertThat(chatModel.lastRequest().messages().getFirst().content()).contains("OpenManus");
    }

    @Test
    @DisplayName("custom role prompt should not replace the default single-agent system prompt globally")
    void customRolePromptShouldNotReplaceTheDefaultSingleAgentSystemPromptGlobally() {
        CapturingChatModel defaultChatModel = new CapturingChatModel("default-result");
        AgentCoordinator defaultCoordinator = AgentCoordinator.builder()
                .aiChatModel(defaultChatModel)
                .aiMemoryProvider(new InMemoryProvider())
                .build();

        CapturingChatModel customChatModel = new CapturingChatModel("custom-result");
        AgentCoordinator customCoordinator = AgentCoordinator.builder()
                .aiChatModel(customChatModel)
                .aiMemoryProvider(new InMemoryProvider())
                .systemMessage("You are a sub agent")
                .build();

        defaultCoordinator.execute("default", "memory-default");
        customCoordinator.execute("custom", "memory-custom");

        assertThat(defaultChatModel.lastRequest().messages().getFirst().content()).contains("OpenManus");
        assertThat(customChatModel.lastRequest().messages().getFirst().content()).isEqualTo("You are a sub agent");
    }

    private static final class CapturingChatModel implements AiChatModel {

        private final String assistantResult;
        private AiChatRequest lastRequest;

        private CapturingChatModel(String assistantResult) {
            this.assistantResult = assistantResult;
        }

        @Override
        public AiChatResponse chat(AiChatRequest request) {
            this.lastRequest = request;
            return new AiChatResponse(
                    AiChatMessage.assistant(assistantResult),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        private AiChatRequest lastRequest() {
            return lastRequest;
        }
    }

    private static final class InMemoryProvider implements AiMemoryProvider {

        @Override
        public AiMemory get(Object memoryId) {
            return new SimpleMemory(memoryId);
        }
    }

    private static final class SimpleMemory implements AiMemory {

        private final Object id;
        private final List<AiChatMessage> messages = new ArrayList<>();

        private SimpleMemory(Object id) {
            this.id = id;
        }

        @Override
        public Object id() {
            return id;
        }

        @Override
        public List<AiChatMessage> messages() {
            return messages;
        }

        @Override
        public void add(AiChatMessage message) {
            messages.add(message);
        }

        @Override
        public void clear() {
            messages.clear();
        }
    }
}
