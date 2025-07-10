package com.openmanus.java.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Reflection tool
 * Using langchain4j @Tool annotation
 */
@Component
public class ReflectionTool {
    
    private static final Logger logger = LoggerFactory.getLogger(ReflectionTool.class);
    
    // Store task execution history for reflection
    private final Map<String, TaskRecord> taskHistory = new ConcurrentHashMap<>();
    
    @Tool("Record task execution process for subsequent reflection")
    public String recordTask(@P("Task ID") String taskId, 
                           @P("Task description") String taskDescription,
                           @P("Execution steps") String steps,
                           @P("Tools used") String toolsUsed,
                           @P("Execution result") String result) {
        try {
            TaskRecord record = new TaskRecord(
                taskId,
                taskDescription,
                steps,
                toolsUsed,
                result,
                LocalDateTime.now()
            );
            taskHistory.put(taskId, record);
            logger.info("Record task execution: {}", taskId);
            return "Task execution record saved, available for subsequent reflection analysis";
        } catch (Exception e) {
            logger.error("Failed to record task", e);
            return "Failed to record task: " + e.getMessage();
        }
    }
    
    @Tool("Perform reflection analysis on specified task")
    public String reflectOnTask(@P("Task ID") String taskId) {
        try {
            TaskRecord record = taskHistory.get(taskId);
            if (record == null) {
                return "Task record not found: " + taskId;
            }
            
            String reflection = String.format("""
                Task Reflection Analysis:
                
                Task Information:
                - ID: %s
                - Description: %s
                - Execution time: %s
                
                Execution Process:
                - Steps: %s
                - Tools used: %s
                - Result: %s
                
                Reflection Points:
                1. Is the reasoning process reasonable?
                2. Are the tool choices appropriate?
                3. How is the execution efficiency?
                4. How is the result quality?
                5. What are the areas for improvement?
                
                Please conduct deep reflection based on the above information.
                """,
                record.taskId,
                record.taskDescription,
                record.executionTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                record.steps,
                record.toolsUsed,
                record.result
            );
            
            logger.info("Generate task reflection: {}", taskId);
            return reflection;
        } catch (Exception e) {
            logger.error("Failed to reflect on task", e);
            return "Failed to reflect on task: " + e.getMessage();
        }
    }
    
    @Tool("Get all task history records")
    public String getTaskHistory() {
        try {
            if (taskHistory.isEmpty()) {
                return "No task history records available";
            }
            
            StringBuilder sb = new StringBuilder("Task History Records:\n");
            taskHistory.values().stream()
                .sorted((a, b) -> b.executionTime.compareTo(a.executionTime))
                .forEach(record -> {
                    sb.append(String.format("""
                        
                        ID: %s
                        Description: %s
                        Time: %s
                        Result: %s
                        ---
                        """,
                        record.taskId,
                        record.taskDescription,
                        record.executionTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        record.result.length() > 100 ? record.result.substring(0, 100) + "..." : record.result
                    ));
                });
            
            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to get task history", e);
            return "Failed to get task history: " + e.getMessage();
        }
    }
    
    /**
     * Task record inner class
     */
    private static class TaskRecord {
        final String taskId;
        final String taskDescription;
        final String steps;
        final String toolsUsed;
        final String result;
        final LocalDateTime executionTime;
        
        TaskRecord(String taskId, String taskDescription, String steps, 
                  String toolsUsed, String result, LocalDateTime executionTime) {
            this.taskId = taskId;
            this.taskDescription = taskDescription;
            this.steps = steps;
            this.toolsUsed = toolsUsed;
            this.result = result;
            this.executionTime = executionTime;
        }
    }
} 