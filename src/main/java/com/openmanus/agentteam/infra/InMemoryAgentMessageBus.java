package com.openmanus.agentteam.infra;

import com.openmanus.agentteam.domain.model.AgentMessage;
import com.openmanus.agentteam.domain.port.AgentMessageBusPort;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory mailbox implementation for agent-to-agent communication.
 */
public class InMemoryAgentMessageBus implements AgentMessageBusPort {

    private final ConcurrentHashMap<String, List<AgentMessage>> mailboxes = new ConcurrentHashMap<>();

    @Override
    public void send(AgentMessage message) {
        if (message == null || message.toAgentId() == null || message.toAgentId().isBlank()) {
            return;
        }
        mailboxes.computeIfAbsent(message.toAgentId(), ignored -> new ArrayList<>());
        List<AgentMessage> mailbox = mailboxes.get(message.toAgentId());
        synchronized (mailbox) {
            mailbox.add(message);
        }
    }

    @Override
    public List<AgentMessage> fetchUnread(String agentId) {
        List<AgentMessage> mailbox = mailboxes.get(agentId);
        if (mailbox == null) {
            return List.of();
        }
        synchronized (mailbox) {
            List<AgentMessage> unread = new ArrayList<>();
            for (AgentMessage message : mailbox) {
                if (!message.read()) {
                    unread.add(message);
                }
            }
            return unread;
        }
    }

    @Override
    public void markAsRead(String agentId, List<String> messageIds) {
        List<AgentMessage> mailbox = mailboxes.get(agentId);
        if (mailbox == null || messageIds == null || messageIds.isEmpty()) {
            return;
        }
        Set<String> readIds = new HashSet<>(messageIds);
        synchronized (mailbox) {
            for (int i = 0; i < mailbox.size(); i++) {
                AgentMessage message = mailbox.get(i);
                if (message != null && message.messageId() != null && readIds.contains(message.messageId())) {
                    mailbox.set(i, message.markRead(System.currentTimeMillis()));
                }
            }
        }
    }
}
