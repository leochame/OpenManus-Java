package com.openmanus.infra.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.Marker;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket日志追加器
 * 只推送标记为 TO_FRONTEND 的重要日志到前端
 */
public class WebSocketLogAppender extends AppenderBase<ILoggingEvent> {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @Override
    protected void append(ILoggingEvent eventObject) {
        // 【核心过滤】只有包含 TO_FRONTEND marker 的日志才会被发送到前端
        Marker marker = eventObject.getMarker();
        if (marker == null || !marker.contains(LogMarkers.TO_FRONTEND)) {
            return; // 非重要日志，直接忽略
        }

        try {
            // 获取会话ID
            String sessionId = eventObject.getMDCPropertyMap().get("sessionId");
            if (sessionId == null || sessionId.trim().isEmpty()) {
                return; // 没有sessionId，无法路由日志
            }

            // 获取日志中继服务
            LogRelayService logRelayService = LogRelayService.getInstance();
            if (logRelayService == null) {
                // 如果服务尚未初始化，则无法发送日志
                return;
            }

            // 构建日志消息
            Map<String, Object> logMessage = new HashMap<>();
            logMessage.put("timestamp", formatter.format(Instant.ofEpochMilli(eventObject.getTimeStamp())));
            logMessage.put("level", eventObject.getLevel().toString());
            logMessage.put("thread", eventObject.getThreadName());
            logMessage.put("logger", eventObject.getLoggerName());
            logMessage.put("message", eventObject.getFormattedMessage());

            // 发送到前端
            logRelayService.relayLog(sessionId, logMessage);

        } catch (Exception e) {
            // 在日志组件内部，我们避免打印异常堆栈以防止无限循环
            // 可以考虑记录到一个紧急文件中
        }
    }
}
