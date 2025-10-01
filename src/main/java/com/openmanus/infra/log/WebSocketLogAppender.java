package com.openmanus.infra.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.openmanus.infra.config.SpringContextHolder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class WebSocketLogAppender extends AppenderBase<ILoggingEvent> {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @Override
    protected void append(ILoggingEvent eventObject) {
        // LogRelayService是Spring Bean，只有在Spring上下文完全初始化后才能获取
        if (!SpringContextHolder.isContextInitialized()) {
            return;
        }

        try {
            String sessionId = eventObject.getMDCPropertyMap().get("sessionId");
            if (sessionId == null || sessionId.trim().isEmpty()) {
                return; // 没有sessionId，无法路由日志
            }

            LogRelayService logRelayService = SpringContextHolder.getBean(LogRelayService.class);
            if (logRelayService == null) {
                // 如果无法获取中继服务，则无法发送日志
                return;
            }

            Map<String, Object> logMessage = new HashMap<>();
            logMessage.put("timestamp", formatter.format(Instant.ofEpochMilli(eventObject.getTimeStamp())));
            logMessage.put("level", eventObject.getLevel().toString());
            logMessage.put("thread", eventObject.getThreadName());
            logMessage.put("logger", eventObject.getLoggerName());
            logMessage.put("message", eventObject.getFormattedMessage());

            logRelayService.relayLog(sessionId, logMessage);

        } catch (Exception e) {
            // 在日志组件内部，我们避免打印异常堆栈以防止无限循环
            // 可以考虑记录到一个紧急文件中
        }
    }
}
