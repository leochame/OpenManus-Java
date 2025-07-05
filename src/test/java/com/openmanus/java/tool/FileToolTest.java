package com.openmanus.java.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(com.openmanus.java.config.TestConfig.class)
@TestPropertySource(properties = {
    "spring.main.web-application-type=none",
    "spring.main.lazy-initialization=true"
})
class FileToolTest {

    private FileTool fileTool;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileTool = new FileTool();
    }

    @Test
    void testFileToolCreation() {
        assertNotNull(fileTool);
        assertEquals("file_operations", fileTool.getName());
        assertNotNull(fileTool.getDescription());
    }

    @Test
    void testReadExistingFile() throws IOException {
        // Create a test file
        Path testFile = tempDir.resolve("test.txt");
        String content = "Hello, World!\nThis is a test file.";
        Files.write(testFile, content.getBytes());
        
        try {
            String result = fileTool.readFile(testFile.toString());
            
            assertNotNull(result);
            assertEquals(content, result);
        } catch (Exception e) {
            fail("Reading existing file should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testReadNonExistentFile() {
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");
        
        assertThrows(Exception.class, () -> {
            fileTool.readFile(nonExistentFile.toString());
        });
    }

    @Test
    void testWriteNewFile() throws IOException {
        Path newFile = tempDir.resolve("new.txt");
        String content = "This is new content.";
        
        try {
            fileTool.writeFile(newFile.toString(), content);
            
            assertTrue(Files.exists(newFile));
            String readContent = Files.readString(newFile);
            assertEquals(content, readContent);
        } catch (Exception e) {
            fail("Writing new file should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testOverwriteExistingFile() throws IOException {
        // Create an existing file
        Path existingFile = tempDir.resolve("existing.txt");
        String originalContent = "Original content";
        Files.write(existingFile, originalContent.getBytes());
        
        String newContent = "New content";
        
        try {
            fileTool.writeFile(existingFile.toString(), newContent);
            
            String readContent = Files.readString(existingFile);
            assertEquals(newContent, readContent);
            assertNotEquals(originalContent, readContent);
        } catch (Exception e) {
            fail("Overwriting existing file should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testAppendToFile() throws IOException {
        Path testFile = tempDir.resolve("append.txt");
        String originalContent = "Original content\n";
        Files.write(testFile, originalContent.getBytes());
        
        String appendContent = "Appended content";
        
        try {
            fileTool.appendToFile(testFile.toString(), appendContent);
            
            String readContent = Files.readString(testFile);
            assertTrue(readContent.contains(originalContent.trim()));
            assertTrue(readContent.contains(appendContent));
        } catch (Exception e) {
            fail("Appending to file should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testListDirectory() throws IOException {
        // Create some test files and directories
        Files.createFile(tempDir.resolve("file1.txt"));
        Files.createFile(tempDir.resolve("file2.txt"));
        Files.createDirectory(tempDir.resolve("subdir"));
        
        try {
            String result = fileTool.listDirectory(tempDir.toString());
            
            assertNotNull(result);
            assertTrue(result.contains("file1.txt"));
            assertTrue(result.contains("file2.txt"));
            assertTrue(result.contains("subdir"));
        } catch (Exception e) {
            fail("Listing directory should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testListNonExistentDirectory() {
        Path nonExistentDir = tempDir.resolve("nonexistent");
        
        assertThrows(Exception.class, () -> {
            fileTool.listDirectory(nonExistentDir.toString());
        });
    }

    @Test
    void testCreateDirectory() {
        Path newDir = tempDir.resolve("newdir");
        
        try {
            fileTool.createDirectory(newDir.toString());
            
            assertTrue(Files.exists(newDir));
            assertTrue(Files.isDirectory(newDir));
        } catch (Exception e) {
            fail("Creating directory should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testCreateNestedDirectory() {
        Path nestedDir = tempDir.resolve("parent").resolve("child");
        
        try {
            fileTool.createDirectory(nestedDir.toString());
            
            assertTrue(Files.exists(nestedDir));
            assertTrue(Files.isDirectory(nestedDir));
            assertTrue(Files.exists(nestedDir.getParent()));
        } catch (Exception e) {
            fail("Creating nested directory should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testDeleteFile() throws IOException {
        Path testFile = tempDir.resolve("delete.txt");
        Files.write(testFile, "content".getBytes());
        
        assertTrue(Files.exists(testFile));
        
        try {
            fileTool.deleteFile(testFile.toString());
            
            assertFalse(Files.exists(testFile));
        } catch (Exception e) {
            fail("Deleting file should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testDeleteNonExistentFile() {
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");
        
        assertThrows(Exception.class, () -> {
            fileTool.deleteFile(nonExistentFile.toString());
        });
    }

    @Test
    void testFileExists() throws IOException {
        Path existingFile = tempDir.resolve("existing.txt");
        Files.write(existingFile, "content".getBytes());
        
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");
        
        try {
            assertTrue(fileTool.fileExists(existingFile.toString()));
            assertFalse(fileTool.fileExists(nonExistentFile.toString()));
        } catch (Exception e) {
            fail("Checking file existence should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testGetFileInfo() throws IOException {
        Path testFile = tempDir.resolve("info.txt");
        String content = "Test content for file info";
        Files.write(testFile, content.getBytes());
        
        try {
            String info = fileTool.getFileInfo(testFile.toString());
            
            assertNotNull(info);
            assertTrue(info.contains("size") || info.contains("Size"));
            assertTrue(info.contains("modified") || info.contains("Modified"));
        } catch (Exception e) {
            fail("Getting file info should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testReadLargeFile() throws IOException {
        Path largeFile = tempDir.resolve("large.txt");
        StringBuilder content = new StringBuilder();
        
        // Create a file with 1000 lines
        for (int i = 0; i < 1000; i++) {
            content.append("Line ").append(i).append("\n");
        }
        
        Files.write(largeFile, content.toString().getBytes());
        
        try {
            String result = fileTool.readFile(largeFile.toString());
            
            assertNotNull(result);
            assertTrue(result.contains("Line 0"));
            assertTrue(result.contains("Line 999"));
        } catch (Exception e) {
            fail("Reading large file should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testFilePermissions() throws IOException {
        Path testFile = tempDir.resolve("permissions.txt");
        Files.write(testFile, "content".getBytes());
        
        try {
            // Test reading file permissions
            String permissions = fileTool.getFilePermissions(testFile.toString());
            assertNotNull(permissions);
            
            // Test setting file permissions (if supported)
            if (System.getProperty("os.name").toLowerCase().contains("nix") ||
                System.getProperty("os.name").toLowerCase().contains("nux") ||
                System.getProperty("os.name").toLowerCase().contains("mac")) {
                
                fileTool.setFilePermissions(testFile.toString(), "644");
                // Verify permissions were set (basic check)
                assertTrue(Files.isReadable(testFile));
                assertTrue(Files.isWritable(testFile));
            }
        } catch (Exception e) {
            // Permission operations might not be supported on all systems
            assertTrue(e.getMessage().contains("permission") || 
                      e.getMessage().contains("not supported"));
        }
    }
}