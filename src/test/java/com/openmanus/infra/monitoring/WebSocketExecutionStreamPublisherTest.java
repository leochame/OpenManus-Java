package com.openmanus.infra.monitoring;

import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.model.ExecutionResultView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("WebSocketExecutionStreamPublisher Tests")
class WebSocketExecutionStreamPublisherTest {

    private static final String TOPIC = "/topic/executions/session-1/execution-1";

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final WebSocketExecutionStreamPublisher publisher = new WebSocketExecutionStreamPublisher(messagingTemplate);

    @Test
    @DisplayName("should publish llm request to raw thought log stream")
    void shouldPublishLlmRequestToRawThoughtLogStream() {
        AgentExecutionEvent event = AgentExecutionEvent.builder()
                .sessionId("session-1")
                .eventType(AgentExecutionEvent.EventType.LLM_REQUEST)
                .input("think about next step")
                .build();

        publisher.publishEvent(TOPIC, event);

        verify(messagingTemplate).convertAndSend(
                eq(TOPIC + "/logs"),
                (Object) argThat(payload -> hasLogMessage(payload, "模型请求\nthink about next step"))
        );
    }

    @Test
    @DisplayName("should publish execution completion marker to raw thought log stream")
    void shouldPublishExecutionCompletionMarkerToRawThoughtLogStream() {
        AgentExecutionEvent event = AgentExecutionEvent.builder()
                .sessionId("session-1")
                .agentType("EXECUTION_COMPLETE")
                .eventType(AgentExecutionEvent.EventType.AGENT_END)
                .output("执行出错: boom")
                .status("ERROR")
                .build();

        publisher.publishEvent(TOPIC, event);

        verify(messagingTemplate).convertAndSend(
                eq(TOPIC + "/logs"),
                (Object) argThat(payload -> hasLogMessage(payload, "执行完成\n执行出错: boom"))
        );
    }

    @Test
    @DisplayName("should publish final result to matching execution topic")
    void shouldPublishFinalResultToMatchingExecutionTopic() {
        ExecutionResultView result = ExecutionResultView.builder()
                .sessionId("session-1")
                .result("hello")
                .status("SUCCESS")
                .build();

        publisher.publishResult(TOPIC, result);

        verify(messagingTemplate).convertAndSend(
                eq(TOPIC + "/result"),
                (Object) argThat(payload -> payload instanceof ExecutionResultView view
                        && "hello".equals(view.getResult()))
        );
    }

    @SuppressWarnings("unchecked")
    private boolean hasLogMessage(Object payload, String expectedMessage) {
        if (!(payload instanceof Map<?, ?> map)) {
            return false;
        }
        Object message = ((Map<String, Object>) map).get("message");
        return expectedMessage.equals(message);
    }
}
