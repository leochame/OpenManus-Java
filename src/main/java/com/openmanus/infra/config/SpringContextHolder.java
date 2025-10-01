package com.openmanus.infra.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spring上下文持有者，允许在非Spring管理的类中访问Spring Beans
 * 特别用于在Logback Appenders和其他框架组件中获取Spring服务
 */
@Component
public class SpringContextHolder implements ApplicationContextAware, ApplicationListener<ContextRefreshedEvent> {

    private static ApplicationContext applicationContext;
    private static final AtomicBoolean contextInitialized = new AtomicBoolean(false);

    // 用于调试，将消息写入固定文件
    private static void debugLog(String message) {
        try {
            File file = new File("spring-context-holder-debug.log");
            PrintWriter writer = new PrintWriter(new FileOutputStream(file, true));
            writer.println("[" + System.currentTimeMillis() + "] " + message);
            writer.close();
        } catch (Exception e) {
            // 静默失败
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        debugLog("Application context set: " + (applicationContext != null));
        SpringContextHolder.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        debugLog("Context refreshed event received");
        contextInitialized.set(true);
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public static boolean isContextInitialized() {
        return contextInitialized.get();
    }

    public static <T> T getBean(Class<T> requiredType) {
        if (applicationContext == null) {
            debugLog("getBean called but applicationContext is null");
            return null;
        }
        
        if (!contextInitialized.get()) {
            debugLog("getBean called but Spring context not fully initialized yet");
            return null;
        }
        
        try {
            T bean = applicationContext.getBean(requiredType);
            debugLog("Successfully retrieved bean of type: " + requiredType.getName());
            return bean;
        } catch (Exception e) {
            debugLog("Error getting bean of type " + requiredType.getName() + ": " + e.getMessage());
            return null;
        }
    }
}
