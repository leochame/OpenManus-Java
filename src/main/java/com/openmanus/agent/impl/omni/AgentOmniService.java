package com.openmanus.agent.impl.omni;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;


public interface AgentOmniService {

    @SystemMessage({
            "You are OpenManus, a large language model trained by OpenManus."
    })
    String chat(@MemoryId String conversationId, @UserMessage String userMessage);
} 