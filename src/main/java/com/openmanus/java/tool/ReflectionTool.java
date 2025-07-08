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
 * 反思工具
 * 使用 langchain4j 的 @Tool 注解
 */
@Component
public class ReflectionTool {
    
    private static final Logger logger = LoggerFactory.getLogger(ReflectionTool.class);
    
    // 存储任务执行历史，用于反思
    private final Map<String, TaskRecord> taskHistory = new ConcurrentHashMap<>();
    
    @Tool("记录任务执行过程，用于后续反思")
    public String recordTask(@P("任务ID") String taskId, 
                           @P("任务描述") String taskDescription,
                           @P("执行步骤") String steps,
                           @P("使用工具") String toolsUsed,
                           @P("执行结果") String result) {
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
            logger.info("记录任务执行: {}", taskId);
            return "任务执行记录已保存，可用于后续反思分析";
        } catch (Exception e) {
            logger.error("记录任务失败", e);
            return "记录任务失败: " + e.getMessage();
        }
    }
    
    @Tool("对指定任务进行反思分析")
    public String reflectOnTask(@P("任务ID") String taskId) {
        try {
            TaskRecord record = taskHistory.get(taskId);
            if (record == null) {
                return "未找到任务记录: " + taskId;
            }
            
            String reflection = String.format("""
                任务反思分析：
                
                任务信息：
                - ID: %s
                - 描述: %s
                - 执行时间: %s
                
                执行过程：
                - 步骤: %s
                - 使用工具: %s
                - 结果: %s
                
                反思要点：
                1. 推理过程是否合理？
                2. 工具选择是否恰当？
                3. 执行效率如何？
                4. 结果质量如何？
                5. 有哪些改进空间？
                
                请基于以上信息进行深度反思。
                """,
                record.taskId,
                record.taskDescription,
                record.executionTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                record.steps,
                record.toolsUsed,
                record.result
            );
            
            logger.info("生成任务反思: {}", taskId);
            return reflection;
        } catch (Exception e) {
            logger.error("反思任务失败", e);
            return "反思任务失败: " + e.getMessage();
        }
    }
    
    @Tool("获取所有任务的历史记录")
    public String getTaskHistory() {
        try {
            if (taskHistory.isEmpty()) {
                return "暂无任务历史记录";
            }
            
            StringBuilder sb = new StringBuilder("任务历史记录：\n");
            taskHistory.values().stream()
                .sorted((a, b) -> b.executionTime.compareTo(a.executionTime))
                .forEach(record -> {
                    sb.append(String.format("""
                        
                        ID: %s
                        描述: %s
                        时间: %s
                        结果: %s
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
            logger.error("获取任务历史失败", e);
            return "获取任务历史失败: " + e.getMessage();
        }
    }
    
    /**
     * 任务记录内部类
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