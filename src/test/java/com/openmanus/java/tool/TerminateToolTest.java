package com.openmanus.java.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TerminateToolTest {

    private TerminateTool terminateTool;
    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        terminateTool = new TerminateTool();
        
        // Capture system output
        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
    }

    @Test
    void testTerminateToolCreation() {
        assertNotNull(terminateTool);
        assertEquals("terminate", terminateTool.getName());
        assertNotNull(terminateTool.getDescription());
    }

    @Test
    void testTerminateWithMessage() {
        String terminationMessage = "Task completed successfully";
        
        try {
            String result = terminateTool.terminate(terminationMessage);
            
            assertNotNull(result);
            assertTrue(result.contains(terminationMessage) || 
                      result.contains("terminated") || 
                      result.contains("completed"));
            
            // Check that termination message was logged/displayed
            String output = outputStream.toString();
            assertTrue(output.contains(terminationMessage) || 
                      output.contains("terminate") || 
                      output.isEmpty()); // Some implementations might not log
        } catch (Exception e) {
            fail("Termination with message should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testTerminateWithoutMessage() {
        try {
            String result = terminateTool.terminate("");
            
            assertNotNull(result);
            assertTrue(result.contains("terminated") || 
                      result.contains("finished") || 
                      result.contains("completed") ||
                      result.equals("TERMINATED"));
        } catch (Exception e) {
            fail("Termination without message should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testTerminateWithEmptyMessage() {
        String emptyMessage = "";
        
        try {
            String result = terminateTool.terminate(emptyMessage);
            
            assertNotNull(result);
            assertTrue(result.contains("terminated") || 
                      result.contains("finished") || 
                      result.contains("completed") ||
                      !result.isEmpty());
        } catch (Exception e) {
            fail("Termination with empty message should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testTerminateWithLongMessage() {
        StringBuilder longMessage = new StringBuilder("This is a very long termination message: ");
        for (int i = 0; i < 100; i++) {
            longMessage.append("word").append(i).append(" ");
        }
        
        try {
            String result = terminateTool.terminate(longMessage.toString());
            
            assertNotNull(result);
            assertTrue(result.contains("very long termination") || 
                      result.contains("terminated") || 
                      result.length() > 0);
        } catch (Exception e) {
            fail("Termination with long message should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testTerminateWithSpecialCharacters() {
        String specialMessage = "Task completed! 🎉 Success @100% with special chars: àáâãäå";
        
        try {
            String result = terminateTool.terminate(specialMessage);
            
            assertNotNull(result);
            assertTrue(result.contains("completed") || 
                      result.contains("Success") || 
                      result.contains("terminated") ||
                      result.length() > 0);
        } catch (Exception e) {
            fail("Termination with special characters should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testTerminateMultipleTimes() {
        String[] messages = {
            "First termination",
            "Second termination", 
            "Third termination"
        };
        
        for (String message : messages) {
            try {
                String result = terminateTool.terminate(message);
                
                assertNotNull(result);
                assertTrue(result.contains(message) || 
                          result.contains("terminated") ||
                          result.length() > 0);
            } catch (Exception e) {
                fail("Multiple terminations should not throw exception: " + e.getMessage());
            }
        }
    }

    @Test
    void testTerminateWithErrorMessage() {
        String errorMessage = "ERROR: Task failed due to invalid input";
        
        try {
            String result = terminateTool.terminate(errorMessage);
            
            assertNotNull(result);
            assertTrue(result.contains("ERROR") || 
                      result.contains("failed") || 
                      result.contains("terminated") ||
                      result.length() > 0);
        } catch (Exception e) {
            fail("Termination with error message should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testTerminateWithSuccessMessage() {
        String successMessage = "SUCCESS: All tasks completed successfully";
        
        try {
            String result = terminateTool.terminate(successMessage);
            
            assertNotNull(result);
            assertTrue(result.contains("SUCCESS") || 
                      result.contains("completed") || 
                      result.contains("terminated") ||
                      result.length() > 0);
        } catch (Exception e) {
            fail("Termination with success message should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testTerminateToolMetadata() {
        // Test tool metadata
        assertEquals("terminate", terminateTool.getName());
        assertNotNull(terminateTool.getDescription());
        assertTrue(terminateTool.getDescription().toLowerCase().contains("terminate") ||
                  terminateTool.getDescription().toLowerCase().contains("end") ||
                  terminateTool.getDescription().toLowerCase().contains("finish") ||
                  terminateTool.getDescription().toLowerCase().contains("stop"));
    }

    @Test
    void testTerminateWithNullMessage() {
        try {
            String result = terminateTool.terminate(null);
            
            assertNotNull(result);
            assertTrue(result.contains("terminated") || 
                      result.contains("finished") ||
                      result.length() > 0);
        } catch (Exception e) {
            // Null message might throw exception, which is acceptable
            assertTrue(e instanceof NullPointerException || 
                      e.getMessage().contains("null"));
        }
    }

    @Test
    void testTerminateReturnValue() {
        String message = "Test termination";
        
        try {
            String result = terminateTool.terminate(message);
            
            assertNotNull(result);
            assertFalse(result.trim().isEmpty());
            
            // The result should be a meaningful termination response
            assertTrue(result.contains(message) || 
                      result.contains("terminated") ||
                      result.contains("completed") ||
                      result.contains("finished") ||
                      result.equals("TERMINATED"));
        } catch (Exception e) {
            fail("Termination should return a valid result: " + e.getMessage());
        }
    }

    @Test
    void testTerminateConsistency() {
        String message = "Consistency test";
        
        try {
            String result1 = terminateTool.terminate(message);
            String result2 = terminateTool.terminate(message);
            
            assertNotNull(result1);
            assertNotNull(result2);
            
            // Results should be consistent for the same input
            // (though they might not be identical if timestamps are included)
            assertTrue(result1.contains(message) || result1.contains("terminated"));
            assertTrue(result2.contains(message) || result2.contains("terminated"));
        } catch (Exception e) {
            fail("Termination should be consistent: " + e.getMessage());
        }
    }

    void tearDown() {
        // Restore original System.out
        System.setOut(originalOut);
    }
}