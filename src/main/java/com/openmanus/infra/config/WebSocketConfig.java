package com.openmanus.infra.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.util.List;

/**
 * WebSocket配置类
 * 
 * 负责配置WebSocket消息代理、端点注册和传输配置
 * 遵循单一职责原则，配置清晰简洁
 */
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final int MESSAGE_SIZE_LIMIT = 128 * 1024; // 128KB
    private static final int SEND_BUFFER_SIZE_LIMIT = 512 * 1024; // 512KB
    private static final int SEND_TIME_LIMIT = 30000; // 30秒
    private static final long HEARTBEAT_INTERVAL = 10000; // 10秒
    private static final long SOCKJS_HEARTBEAT_TIME = 25000; // 25秒
    private static final long SOCKJS_DISCONNECT_DELAY = 60000; // 60秒

    private final MappingJackson2MessageConverter messageConverter;
    
    public WebSocketConfig(MappingJackson2MessageConverter messageConverter) {
        this.messageConverter = messageConverter;
    }

    /**
     * WebSocket心跳任务调度器
     */
    @Bean
    public TaskScheduler webSocketTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic")
              .setHeartbeatValue(new long[]{HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL})
              .setTaskScheduler(webSocketTaskScheduler());
        config.setApplicationDestinationPrefixes("/app");
        
        log.info("WebSocket消息代理配置完成 - 心跳间隔: {}ms", HEARTBEAT_INTERVAL);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
               .setAllowedOriginPatterns("*")
               .setHandshakeHandler(new DefaultHandshakeHandler(new TomcatRequestUpgradeStrategy()))
               .withSockJS()
               .setWebSocketEnabled(true)
               .setHeartbeatTime(SOCKJS_HEARTBEAT_TIME)
               .setDisconnectDelay(SOCKJS_DISCONNECT_DELAY);
        
        log.info("WebSocket端点注册完成 - SockJS心跳: {}ms, 断开延迟: {}ms", 
                SOCKJS_HEARTBEAT_TIME, SOCKJS_DISCONNECT_DELAY);
    }
    
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MESSAGE_SIZE_LIMIT)
                   .setSendBufferSizeLimit(SEND_BUFFER_SIZE_LIMIT)
                   .setSendTimeLimit(SEND_TIME_LIMIT);
        
        log.info("WebSocket传输配置完成 - 消息大小: {}KB, 发送超时: {}ms", 
                MESSAGE_SIZE_LIMIT / 1024, SEND_TIME_LIMIT);
    }
    
    @Override
    public boolean configureMessageConverters(List<org.springframework.messaging.converter.MessageConverter> messageConverters) {
        messageConverters.clear();
        messageConverters.add(messageConverter);
        return true;
    }
} 