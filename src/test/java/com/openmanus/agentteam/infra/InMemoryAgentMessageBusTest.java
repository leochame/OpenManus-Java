package com.openmanus.agentteam.infra;

import com.openmanus.agentteam.domain.model.AgentMessage;
import com.openmanus.agentteam.domain.model.MessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryAgentMessageBus Tests")
class InMemoryAgentMessageBusTest {

    private final InMemoryAgentMessageBus messageBus = new InMemoryAgentMessageBus();

    @Test
    @DisplayName("should fetch unread mailbox messages")
    void shouldFetchUnreadMailboxMessages() {
        AgentMessage message = new AgentMessage(
                "msg-1",
                "agent-a",
                "agent-b",
                "group-1",
                MessageType.INFO,
                "hello",
                false,
                1L,
                null
        );

        messageBus.send(message);

        assertThat(messageBus.fetchUnread("agent-b"))
                .extracting(AgentMessage::content)
                .containsExactly("hello");
    }

    @Test
    @DisplayName("should mark fetched messages as read")
    void shouldMarkFetchedMessagesAsRead() {
        AgentMessage message = new AgentMessage(
                "msg-1",
                "agent-a",
                "agent-b",
                "group-1",
                MessageType.INFO,
                "hello",
                false,
                1L,
                null
        );
        messageBus.send(message);

        messageBus.markAsRead("agent-b", List.of("msg-1"));

        assertThat(messageBus.fetchUnread("agent-b")).isEmpty();
    }
}
