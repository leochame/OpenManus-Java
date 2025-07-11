package com.openmanus.java;

import com.openmanus.java.agent.ManusAgent;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Disabled("This test is disabled because it was written for the old agent architecture and needs to be rewritten for AiServices.")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EndToEndTest {

    @Autowired
    private ManusAgent manusAgent;

    @MockBean
    private ChatModel chatModel;

    @Test
    void shouldReturnAnswer_whenUsingSimpleTool() {
//        String userQuestion = "What is the result of 6 * 7?";
//        String toolCode = "print(6 * 7)";
//
//        AiMessage toolCall = AiMessage.from(
//                ToolExecutionRequest.builder()
//                        .name("executePython")
//                        .arguments(String.format("{\"code\":\"%s\"}", toolCode))
//                        .build()
//        );
//        ChatResponse toolCallResponse = ChatResponse.builder().aiMessage(toolCall).build();
//        ChatResponse finalResponse = ChatResponse.builder().aiMessage(AiMessage.from("The result is: 42")).build();
//
//        when(chatModel.chat(any(ChatRequest.class)))
//                .thenReturn(toolCallResponse)
//                .thenReturn(finalResponse);
//
//        Map<String, Object> response = manusAgent.chatWithCot(userQuestion);
//
//        assertThat(response.get("answer")).isEqualTo("The result is: 42");
    }

    @Test
    void shouldPerformMultiTurnConversation_whenCreatingFileAndHashingIt() {
//        // 1. Define the multi-step user prompt
//        String userPrompt = "Please create a file named 'test.txt' with the content 'hello', " +
//                "and then tell me the MD5 hash of that content.";
//
//        // 2. Mock the multi-turn conversation flow from the ChatModel
//        // The MD5 hash of "hello" is 5d41402abc4b2a76b9719d911017c592
//        String finalAnswer = "5d41402abc4b2a76b9719d911017c592";
//
//        // Turn 1: Model decides to write the file
//        AiMessage writeFileCall = AiMessage.from(
//                ToolExecutionRequest.builder()
//                        .name("writeFile")
//                        .arguments("{\"filePath\":\"test.txt\",\"content\":\"hello\"}")
//                        .build()
//        );
//        ChatResponse response1 = ChatResponse.builder().aiMessage(writeFileCall).build();
//
//        // Turn 2: Model decides to execute python to get the hash
//        AiMessage pythonCall = AiMessage.from(
//                ToolExecutionRequest.builder()
//                        .name("executePython")
//                        .arguments("{\"code\":\"import hashlib; print(hashlib.md5('hello'.encode()).hexdigest())\"}")
//                        .build()
//        );
//        ChatResponse response2 = ChatResponse.builder().aiMessage(pythonCall).build();
//
//        // Turn 3: Model provides the final answer
//        AiMessage finalMessage = AiMessage.from(finalAnswer);
//        ChatResponse response3 = ChatResponse.builder().aiMessage(finalMessage).build();
//
//        when(chatModel.chat(any(ChatRequest.class)))
//                .thenReturn(response1) // First call, returns tool call
//                .thenReturn(response2) // Second call, returns another tool call
//                .thenReturn(response3); // Third call, returns final answer
//
//        // 3. Execute the agent
//        Map<String, Object> response = manusAgent.chatWithCot(userPrompt);
//
//        // 4. Assert the final answer
//        assertThat(response.get("answer")).isNotNull();
//        assertThat(response.get("answer").toString()).contains(finalAnswer);
//
//        // Optional: Clean up the created file
//        new java.io.File("test.txt").delete();
    }
} 