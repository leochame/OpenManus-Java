package com.openmanus.infra.log;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;

/**
 * 日志中继服务
 * 作为一个标准的Spring Bean，负责将从Logback Appender接收到的日志消息
 * 通过WebSocket安全地发送出去。
 */
@Service
public class LogRelayService {

    private final SimpMessagingTemplate messagingTemplate;

    // 用于静态访问的实例
    private static LogRelayService instance;

    @Autowired
    public LogRelayService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void init() {
        // 在bean初始化后，将自身赋值给静态实例
        instance = this;
    }

    /**
     * 获取静态实例
     * @return LogRelayService的实例
     */
    public static LogRelayService getInstance() {
        return instance;
    }

    /**
     * 中继日志消息到指定的WebSocket主题
     * @param sessionId 会话ID
     * @param logMessage 格式化后的日志消息
     */
    public void relayLog(String sessionId, Map<String, Object> logMessage) {
        if (sessionId == null || logMessage == null) {
            return;
        }
        String destination = "/topic/executions/" + sessionId + "/logs";
        messagingTemplate.convertAndSend(destination, logMessage);
    }
}
