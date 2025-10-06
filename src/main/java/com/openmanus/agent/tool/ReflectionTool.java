package com.openmanus.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反思工具 - 记录和分析任务执行过程
 * 
 * 功能：
 * 1. 记录任务执行历史
 * 2. 提供反思分析框架
 * 3. 查询历史记录
 * 
 * 采用 Record 模式简化数据对象
 */
@Component
@Slf4j
public class ReflectionTool {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_RESULT_PREVIEW_LENGTH = 100;
    
    // 任务执行历史记录
    private final Map<String, TaskRecord> taskHistory = new ConcurrentHashMap<>();
    
    @Tool("记录任务执行过程，用于后续反思")
    public String recordTask(@P("任务ID") String taskId, 
                           @P("任务描述") String taskDescription,
                           @P("执行步骤") String steps,
                           @P("使用的工具") String toolsUsed,
                           @P("执行结果") String result) {
        try {
            TaskRecord record = new TaskRecord(taskId, taskDescription, steps, toolsUsed, result, LocalDateTime.now());
            taskHistory.put(taskId, record);
            log.info("记录任务执行: {}", taskId);
            return "任务执行记录已保存，可进行后续反思分析";
        } catch (Exception e) {
            log.error("记录任务失败", e);
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
            
            String reflection = """
                📋 任务反思分析
                
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
                """.formatted(
                    record.taskId(),
                    record.taskDescription(),
                    record.executionTime().format(DATE_FORMATTER),
                    record.steps(),
                    record.toolsUsed(),
                    record.result()
                );
            
            log.info("生成任务反思: {}", taskId);
            return reflection;
        } catch (Exception e) {
            log.error("任务反思失败", e);
            return "任务反思失败: " + e.getMessage();
        }
    }
    
    @Tool("获取所有任务历史记录")
    public String getTaskHistory() {
        try {
            if (taskHistory.isEmpty()) {
                return "暂无任务历史记录";
            }
            
            StringBuilder sb = new StringBuilder("📚 任务历史记录\n\n");
            taskHistory.values().stream()
                .sorted((a, b) -> b.executionTime().compareTo(a.executionTime()))
                .forEach(record -> sb.append(formatHistoryRecord(record)));
            
            return sb.toString();
        } catch (Exception e) {
            log.error("获取任务历史失败", e);
            return "获取任务历史失败: " + e.getMessage();
        }
    }
    
    /**
     * 格式化历史记录条目
     */
    private String formatHistoryRecord(TaskRecord record) {
        String resultPreview = record.result().length() > MAX_RESULT_PREVIEW_LENGTH 
            ? record.result().substring(0, MAX_RESULT_PREVIEW_LENGTH) + "..." 
            : record.result();
        
        return """
            ID: %s
            描述: %s
            时间: %s
            结果: %s
            ---
            """.formatted(
                record.taskId(),
                record.taskDescription(),
                record.executionTime().format(DATE_FORMATTER),
                resultPreview
            );
    }
    
    /**
     * 任务记录 - 使用 Record 简化不可变数据对象
     */
    record TaskRecord(
        String taskId,
        String taskDescription,
        String steps,
        String toolsUsed,
        String result,
        LocalDateTime executionTime
    ) {}
} 