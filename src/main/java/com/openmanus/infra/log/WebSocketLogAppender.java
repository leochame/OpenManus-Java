package com.openmanus.infra.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 日志追加器（过滤器模式）
 * 
 * 职责：只推送标记为 TO_FRONTEND 的重要日志到前端
 * 
 * 工作流程：
 * 1. 过滤：只处理带有 TO_FRONTEND 标记的日志
 * 2. 提取：从 MDC 中获取 sessionId
 * 3. 构建：格式化日志消息
 * 4. 发送：通过 LogRelayService 发送到前端
 */
public class WebSocketLogAppender extends AppenderBase<ILoggingEvent> {

    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @Override
    protected void append(ILoggingEvent event) {
        if (!shouldProcess(event)) {
            return;
        }

        try {
            String sessionId = extractSessionId(event);
            if (sessionId == null) {
                return;
            }

            LogRelayService service = LogRelayService.getInstance();
            if (service == null) {
                return;
            }

            Map<String, Object> logMessage = buildLogMessage(event);
            service.relayLog(sessionId, logMessage);

        } catch (Exception e) {
            // 避免日志循环，静默处理异常
        }
    }

    private boolean shouldProcess(ILoggingEvent event) {
        return event.getMarker() != null && 
               event.getMarker().contains(LogMarkers.TO_FRONTEND);
    }

    private String extractSessionId(ILoggingEvent event) {
        String sessionId = event.getMDCPropertyMap().get("sessionId");
        return (sessionId != null && !sessionId.trim().isEmpty()) ? sessionId : null;
    }

    private Map<String, Object> buildLogMessage(ILoggingEvent event) {
        Map<String, Object> message = new HashMap<>();
        message.put("timestamp", TIME_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp())));
        message.put("level", event.getLevel().toString());
        message.put("thread", event.getThreadName());
        message.put("logger", event.getLoggerName());
        message.put("message", event.getFormattedMessage());
        return message;
    }
}
