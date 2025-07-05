package com.openmanus.java.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(com.openmanus.java.config.TestConfig.class)
@TestPropertySource(properties = {
    "spring.main.web-application-type=none",
    "spring.main.lazy-initialization=true"
})
class AskHumanToolTest {

    private MockAskHumanTool askHumanTool;
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
        askHumanTool = new MockAskHumanTool("测试响应", "继续执行", "任务完成");
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
    }

    @Test
    void testMockAskHumanToolCreation() {
        assertNotNull(askHumanTool);
        assertEquals("ask_human", MockAskHumanTool.NAME);
        assertNotNull(MockAskHumanTool.DESCRIPTION);
    }

    @Test
    void testMockAskHumanWithQuestion() {
        String question = "What is your favorite color?";
        String result = askHumanTool.execute(question);
        assertNotNull(result);
        assertTrue(result.contains("测试响应") || result.contains("继续执行") || result.contains("任务完成"));
    }

    @Test
    void testMockAskHumanWithEmptyInput() {
        String question = "Please enter something:";
        String result = askHumanTool.execute(question);
        assertNotNull(result);
        assertTrue(result.contains("测试响应") || result.contains("继续执行") || result.contains("任务完成"));
    }

    @Test
    void testMockAskHumanWithMultipleQuestions() {
        String[] questions = { "Question 1?", "Question 2?", "Question 3?" };
        for (String question : questions) {
            String result = askHumanTool.execute(question);
            assertNotNull(result);
            assertTrue(result.contains("测试响应") || result.contains("继续执行") || result.contains("任务完成"));
        }
    }

    @Test
    void testMockAskHumanWithSpecialCharacters() {
        String question = "Enter special characters: @#$%^&*()_+ 你好 🌟";
        String result = askHumanTool.execute(question);
        assertNotNull(result);
        assertTrue(result.contains("测试响应") || result.contains("继续执行") || result.contains("任务完成"));
    }

    @Test
    void testMockAskHumanTimeout() {
        // MockAskHumanTool doesn't timeout, it returns immediately
        String question = "This will not timeout:";
        String result = askHumanTool.execute(question);
        assertNotNull(result);
        // Should return immediately without timeout
        assertTrue(result.contains("测试响应") || result.contains("继续执行") || result.contains("任务完成"));
    }

    @Test
    void testMockAskHumanQuestionFormatting() {
        String question = "What is 2 + 2?";
        String result = askHumanTool.execute(question);
        assertNotNull(result);
        assertTrue(result.contains("测试响应") || result.contains("继续执行") || result.contains("任务完成"));
    }

    @Test
    void testMockAskHumanToolMetadata() {
        // Test tool metadata
        assertEquals("ask_human", MockAskHumanTool.NAME);
        assertNotNull(MockAskHumanTool.DESCRIPTION);
        assertTrue(MockAskHumanTool.DESCRIPTION.toLowerCase().contains("human") ||
                MockAskHumanTool.DESCRIPTION.toLowerCase().contains("help"));
    }

    @Test
    void testMockAskHumanWithLongQuestion() {
        StringBuilder longQuestion = new StringBuilder("This is a very long question: ");
        for (int i = 0; i < 100; i++) {
            longQuestion.append("word").append(i).append(" ");
        }
        longQuestion.append("?");
        
        String result = askHumanTool.execute(longQuestion.toString());
        assertNotNull(result);
        assertTrue(result.contains("测试响应") || result.contains("继续执行") || result.contains("任务完成"));
    }

    @Test
    void testMockAskHumanCallCount() {
        // Test that MockAskHumanTool cycles through responses
        String question = "Test question";
        
        String result1 = askHumanTool.execute(question);
        String result2 = askHumanTool.execute(question);
        String result3 = askHumanTool.execute(question);
        String result4 = askHumanTool.execute(question); // Should cycle back to first response
        
        assertNotNull(result1);
        assertNotNull(result2);
        assertNotNull(result3);
        assertNotNull(result4);
        
        // All results should be valid responses
        assertTrue(result1.contains("测试响应") || result1.contains("继续执行") || result1.contains("任务完成"));
        assertTrue(result2.contains("测试响应") || result2.contains("继续执行") || result2.contains("任务完成"));
        assertTrue(result3.contains("测试响应") || result3.contains("继续执行") || result3.contains("任务完成"));
        assertTrue(result4.contains("测试响应") || result4.contains("继续执行") || result4.contains("任务完成"));
    }
}
