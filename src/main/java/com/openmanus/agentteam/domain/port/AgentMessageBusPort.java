package com.openmanus.agentteam.domain.port;

import com.openmanus.agentteam.domain.model.AgentMessage;

import java.util.List;

public interface AgentMessageBusPort {

    void send(AgentMessage message);

    List<AgentMessage> fetchUnread(String agentId);

    void markAsRead(String agentId, List<String> messageIds);
}
