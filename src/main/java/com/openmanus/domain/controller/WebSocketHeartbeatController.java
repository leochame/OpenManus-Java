package com.openmanus.domain.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket心跳控制器
 * 处理WebSocket连接的心跳请求，确保连接稳定
 */
@Controller
@Slf4j
public class WebSocketHeartbeatController {

    /**
     * 处理WebSocket心跳请求
     * @param heartbeatMessage 心跳消息
     * @param principal 用户主体
     * @return 心跳响应
     */
    @MessageMapping("/heartbeat")
    @SendToUser("/queue/heartbeat")
    public Map<String, Object> handleHeartbeat(Map<String, Object> heartbeatMessage, Principal principal) {
        long receivedTimestamp = heartbeatMessage.containsKey("timestamp") ? 
                Long.parseLong(heartbeatMessage.get("timestamp").toString()) : 0;
        
        Map<String, Object> response = new HashMap<>();
        response.put("serverTime", System.currentTimeMillis());
        response.put("clientTime", receivedTimestamp);
        response.put("status", "ok");
        
        if (log.isDebugEnabled()) {
            log.debug("收到来自[{}]的心跳请求，客户端时间: {}", 
                    principal != null ? principal.getName() : "匿名用户", 
                    receivedTimestamp);
        }
        
        return response;
    }
} 