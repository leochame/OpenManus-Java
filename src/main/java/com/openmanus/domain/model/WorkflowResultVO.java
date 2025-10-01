package com.openmanus.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工作流结果值对象
 * 用于传递工作流执行的最终结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowResultVO {
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 消息类型
     */
    private String messageType = "WORKFLOW_RESULT";
    
    /**
     * 用户输入
     */
    private String userInput;
    
    /**
     * 最终结果
     */
    private String result;
    
    /**
     * 执行状态
     */
    private String status;
    
    /**
     * 完成时间
     */
    private LocalDateTime completedTime;
    
    /**
     * 执行时长(毫秒)
     */
    private Long executionTime;
} 