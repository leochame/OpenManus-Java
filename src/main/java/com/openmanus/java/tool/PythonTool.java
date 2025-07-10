package com.openmanus.java.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Python code execution tool
 * Using langchain4j @Tool annotation
 */
@Component
public class PythonTool {
    
    private static final Logger logger = LoggerFactory.getLogger(PythonTool.class);

    @Tool("Execute Python code")
    public String executePython(@P("Python code") String code) {
        try {
            // Create temporary file
            String tempDir = System.getProperty("java.io.tmpdir");
            String fileName = "python_" + UUID.randomUUID().toString().substring(0, 8) + ".py";
            Path filePath = Paths.get(tempDir, fileName);
            
            // Write code to file
            Files.write(filePath, code.getBytes());
            
            // Execute Python code
            ProcessBuilder processBuilder = new ProcessBuilder("python3", filePath.toString());
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // Wait for process to complete
            int exitCode = process.waitFor();
            
            // Clean up temporary file
            Files.deleteIfExists(filePath);
            
            if (exitCode == 0) {
                return "Execution successful:\n" + output.toString();
            } else {
                return "Execution failed (exit code: " + exitCode + "):\n" + output.toString();
            }
            
        } catch (IOException | InterruptedException e) {
            logger.error("Python execution failed", e);
            return "Execution failed: " + e.getMessage();
            }
    }
    
    @Tool("Execute Python file")
    public String executePythonFile(@P("Python file path") String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return "File does not exist: " + filePath;
                }
            
            // Execute Python file
            ProcessBuilder processBuilder = new ProcessBuilder("python3", filePath);
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // Wait for process to complete
            int exitCode = process.waitFor();
        
            if (exitCode == 0) {
                return "Execution successful:\n" + output.toString();
        } else {
                return "Execution failed (exit code: " + exitCode + "):\n" + output.toString();
    }
    
        } catch (IOException | InterruptedException e) {
            logger.error("Python file execution failed", e);
            return "Execution failed: " + e.getMessage();
        }
    }
}