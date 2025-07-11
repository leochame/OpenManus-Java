package com.openmanus.java.nodes;

import com.openmanus.java.state.OpenManusAgentState;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Deprecated
//@Component
public class ThinkNode implements AsyncNodeAction<OpenManusAgentState> {
    
    private static final Logger logger = LoggerFactory.getLogger(ThinkNode.class);
    
    private final ChatModel chatModel;
    private final List<ToolSpecification> toolSpecifications;

    @Autowired
    public ThinkNode(ChatModel chatModel, List<ToolSpecification> toolSpecifications) {
        this.chatModel = chatModel;
        this.toolSpecifications = toolSpecifications;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OpenManusAgentState state) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Thinking...");

            // Call the model with tools
            AiMessage aiMessage;
            try {
                ChatRequest request = ChatRequest.builder()
                        .messages(state.getMessages())
                        .toolSpecifications(toolSpecifications)
                        .build();

                ChatResponse response = chatModel.chat(request);
                aiMessage = response.aiMessage();

            } catch (Exception e) {
                logger.error("Error while calling the chat model with tools.", e);
                // Return an error state
                Map<String, Object> errorUpdates = new HashMap<>();
                errorUpdates.put("error", "Invalid ChatModel configuration for tool usage.");
                return errorUpdates;
            }

            // Store the AI message and check for tool execution requests
            Map<String, Object> updates = new HashMap<>();
            updates.put(OpenManusAgentState.MESSAGES, aiMessage);
            
            if (aiMessage.hasToolExecutionRequests()) {
                logger.info("LLM requested tool execution: {}", aiMessage.toolExecutionRequests());
                updates.put("next_action", "tool_call");
            } else {
                logger.info("LLM provided a direct answer.");
                updates.put("next_action", "direct_answer");
                updates.put(OpenManusAgentState.FINAL_ANSWER, aiMessage.text());
            }

            // Update state fields
            updates.put(OpenManusAgentState.THOUGHTS, aiMessage.text());
            updates.put("current_state", "thinking_complete");

            return updates;
        });
    }
}