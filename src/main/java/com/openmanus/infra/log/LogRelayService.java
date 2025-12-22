package com.openmanus.infra.log;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 日志中继服务（单例模式）
 * 
 * 职责：将 Logback Appender 接收到的日志通过 WebSocket 发送到前端
 * 
 * 设计说明：
 * - 使用静态实例方式是因为 Logback Appender 不是 Spring Bean
 * - 通过 @PostConstruct 初始化静态实例，确保 Spring 容器启动后可访问
 */
@Slf4j
@Service
public class LogRelayService {

    private static volatile LogRelayService instance;
    
    private final SimpMessagingTemplate messagingTemplate;

    public LogRelayService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void init() {
        instance = this;
        log.info("LogRelayService initialized");
    }

    public static LogRelayService getInstance() {
        return instance;
    }

    /**
     * 中继日志到前端
     * 如果 WebSocket 会话已关闭，静默忽略错误
     */
    public void relayLog(String sessionId, Map<String, Object> logMessage) {
        if (sessionId == null || logMessage == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSend("/topic/executions/" + sessionId + "/logs", logMessage);
        } catch (Exception e) {
            // 静默处理：WebSocket 会话可能已关闭，这是正常的竞态条件
            log.debug("无法发送日志到会话 {}: {}", sessionId, e.getMessage());
        }
    }
}
