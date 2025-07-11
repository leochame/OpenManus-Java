package com.openmanus.java.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;


public interface ManusAgentService {

    @SystemMessage({
            "You are OpenManus, a large language model trained by OpenManus."
    })
    String chat(@MemoryId String conversationId, @UserMessage String userMessage);
} 