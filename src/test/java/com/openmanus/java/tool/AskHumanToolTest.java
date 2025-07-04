package com.openmanus.java.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class AskHumanToolTest {

    private AskHumanTool askHumanTool;
    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;
    private InputStream originalIn;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        originalIn = System.in;
        System.setOut(new PrintStream(outputStream));
        askHumanTool = new AskHumanTool(System.in);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Restore original streams
        System.setOut(originalOut);
        System.setIn(originalIn);

        // Close mocks
        if (mocks != null) {
            mocks.close();
        }

        // Clean up AskHumanTool resources
        if (askHumanTool != null) {
            askHumanTool.close();
        }
    }

    @Test
    void testAskHumanToolCreation() {
        assertNotNull(askHumanTool);
        assertEquals("ask_human", askHumanTool.getName());
        assertNotNull(askHumanTool.getDescription());
    }

    @Test
    void testAskHumanWithSimulatedInput() {
        String question = "What is your favorite color?";
        String simulatedAnswer = "Blue";
        System.setIn(new ByteArrayInputStream(simulatedAnswer.getBytes()));
        askHumanTool = new AskHumanTool(System.in);
        String result = askHumanTool.askQuestion(question);
        assertNotNull(result);
        assertEquals(simulatedAnswer, result);
        String output = outputStream.toString();
        assertTrue(output.contains(question));
    }

    @Test
    void testAskHumanWithEmptyInput() {
        String question = "Please enter something:";
        String simulatedAnswer = "\n";
        System.setIn(new ByteArrayInputStream(simulatedAnswer.getBytes()));
        askHumanTool = new AskHumanTool(System.in);
        String result = askHumanTool.askQuestion(question);
        assertNotNull(result);
        assertEquals("", result);
    }

    @Test
    void testAskHumanWithMultilineInput() {
        String question = "Please provide a detailed explanation:";
        String simulatedAnswer = "Line 1\nLine 2\nLine 3";
        System.setIn(new ByteArrayInputStream(simulatedAnswer.getBytes()));
        askHumanTool = new AskHumanTool(System.in);
        String result = askHumanTool.askQuestion(question);
        assertNotNull(result);
        assertEquals("Line 1", result);
    }

    @Test
    void testAskHumanWithSpecialCharacters() {
        String question = "Enter special characters:";
        String simulatedAnswer = "Hello! @#$%^&*()_+ 你好 🌟";
        System.setIn(new ByteArrayInputStream(simulatedAnswer.getBytes()));
        askHumanTool = new AskHumanTool(System.in);
        String result = askHumanTool.askQuestion(question);
        assertNotNull(result);
        assertEquals(simulatedAnswer, result);
    }

    @Test
    void testAskHumanTimeout() {
        String question = "This will timeout:";
        System.setIn(new ByteArrayInputStream(new byte[0]));
        askHumanTool = new AskHumanTool(System.in);
        String result = askHumanTool.askQuestionWithTimeout(question, 1);
        assertNotNull(result);
        assertTrue(result.contains("Timeout") || result.contains("timeout"));
    }

    @Test
    void testAskHumanQuestionFormatting() {
        String question = "What is 2 + 2?";
        String simulatedAnswer = "4";
        System.setIn(new ByteArrayInputStream(simulatedAnswer.getBytes()));
        askHumanTool = new AskHumanTool(System.in);
        String result = askHumanTool.askQuestion(question);
        String output = outputStream.toString();
        assertTrue(output.contains(question));
        assertTrue(output.contains("Bot:"));
        assertTrue(output.contains("You:"));
        assertNotNull(result);
        assertEquals("4", result);
    }

    @Test
    void testAskHumanMultipleQuestions() {
        String[] questions = { "Question 1?", "Question 2?", "Question 3?" };
        String[] answers = { "Answer 1", "Answer 2", "Answer 3" };
        for (int i = 0; i < questions.length; i++) {
            System.setIn(new ByteArrayInputStream(answers[i].getBytes()));
            AskHumanTool tool = new AskHumanTool(System.in);
            String result = tool.askQuestion(questions[i]);
            assertNotNull(result);
            assertEquals(answers[i], result);
            tool.close();
        }
    }

    @Test
    void testAskHumanToolMetadata() {
        // Test tool metadata
        assertEquals("ask_human", askHumanTool.getName());
        assertNotNull(askHumanTool.getDescription());
        assertTrue(askHumanTool.getDescription().toLowerCase().contains("human") ||
                askHumanTool.getDescription().toLowerCase().contains("user") ||
                askHumanTool.getDescription().toLowerCase().contains("ask"));
    }

    @Test
    void testAskHumanWithLongQuestion() {
        StringBuilder longQuestion = new StringBuilder("This is a very long question: ");
        for (int i = 0; i < 100; i++) {
            longQuestion.append("word").append(i).append(" ");
        }
        longQuestion.append("?");
        String simulatedAnswer = "Long answer";
        System.setIn(new ByteArrayInputStream(simulatedAnswer.getBytes()));
        askHumanTool = new AskHumanTool(System.in);
        try {
            String result = askHumanTool.askQuestion(longQuestion.toString());

            assertNotNull(result);
            assertEquals("Long answer", result.trim());

            // Check that long question was displayed
            String output = outputStream.toString();
            assertTrue(output.contains("very long question"));
        } catch (Exception e) {
            fail("Long question should not throw exception: " + e.getMessage());
        } finally {
            System.setIn(System.in);
        }
    }

}
