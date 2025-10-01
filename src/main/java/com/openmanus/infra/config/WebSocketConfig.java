package com.openmanus.infra.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompReactorNettyCodec;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final MappingJackson2MessageConverter messageConverter;
    
    public WebSocketConfig(MappingJackson2MessageConverter messageConverter) {
        this.messageConverter = messageConverter;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic")
              .setHeartbeatValue(new long[]{10000, 10000}) // 设置心跳间隔为10秒
              .setTaskScheduler(new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler() {{
                  setPoolSize(2);
                  setThreadNamePrefix("ws-heartbeat-thread-");
                  initialize();
              }});
        config.setApplicationDestinationPrefixes("/app");
        
        // 记录配置信息
        log.info("WebSocket消息代理配置完成，心跳间隔：10秒，应用前缀：/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 增强WebSocket配置，使用明确的升级策略和握手处理器
        registry.addEndpoint("/ws")
               .setAllowedOriginPatterns("*")
               .setHandshakeHandler(new DefaultHandshakeHandler(new TomcatRequestUpgradeStrategy()))
               .withSockJS()
               .setWebSocketEnabled(true)
               .setHeartbeatTime(25000) // 25秒心跳
               .setDisconnectDelay(60000);  // 增加断开延迟到60秒，提高稳定性
        
        log.info("WebSocket端点注册完成，心跳时间：25秒，断开延迟：60秒");
    }
    
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(8192 * 4)  // 增加消息大小限制
                   .setSendBufferSizeLimit(512 * 1024)  // 增加发送缓冲区限制
                   .setSendTimeLimit(30000)  // 增加发送超时时间到30秒
                   .setMessageSizeLimit(128 * 1024); // 设置消息大小限制为128KB
        
        log.info("WebSocket传输配置完成，消息大小限制：128KB，发送超时：30秒");
    }
    
    @Override
    public boolean configureMessageConverters(List<org.springframework.messaging.converter.MessageConverter> messageConverters) {
        // 清除默认转换器
        messageConverters.clear();
        // 添加我们自定义的转换器
        messageConverters.add(messageConverter);
        log.info("WebSocket消息转换器配置完成，使用自定义Jackson消息转换器");
        return true;
    }
} 