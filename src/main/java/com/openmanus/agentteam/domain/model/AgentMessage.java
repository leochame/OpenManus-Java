package com.openmanus.agentteam.domain.model;

/**
 * Mailbox message exchanged between agents.
 */
public record AgentMessage(
        String messageId,
        String fromAgentId,
        String toAgentId,
        String groupId,
        MessageType type,
        String content,
        boolean read,
        long createdAt,
        Long readAt
) {

    public AgentMessage {
        content = content == null ? "" : content;
        type = type == null ? MessageType.INFO : type;
    }

    public AgentMessage markRead(long timestamp) {
        return new AgentMessage(
                messageId,
                fromAgentId,
                toAgentId,
                groupId,
                type,
                content,
                true,
                createdAt,
                timestamp
        );
    }
}
