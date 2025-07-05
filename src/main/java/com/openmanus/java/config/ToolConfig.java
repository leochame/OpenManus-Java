package com.openmanus.java.config;

import com.openmanus.java.tool.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工具配置类，用于创建和配置工具注册表
 */
@Configuration
public class ToolConfig {

    private static final Logger logger = LoggerFactory.getLogger(ToolConfig.class);

    @Autowired
    private OpenManusProperties properties;

    /**
     * 创建工具注册表Bean
     * 
     * @return 配置好的工具注册表实例
     */
    @Bean
    @Primary
    public ToolRegistry toolRegistry() {
        logger.info("🔧 初始化工具注册表...");
        
        try {
            // 创建工具注册表，包含所有可用工具
            ToolRegistry registry = new ToolRegistry(
                new PythonTool(properties),
                new BrowserTool(properties),
                new FileTool(properties),
                new AskHumanTool(),
                new TerminateTool()
            );
            
            logger.info("✅ 工具注册表初始化完成，包含 {} 个工具", registry.getToolCount());
            logger.info("📋 可用工具: {}", registry.getAllTools());
            
            return registry;
            
        } catch (Exception e) {
            logger.error("❌ 工具注册表初始化失败: {}", e.getMessage(), e);
            
            // 如果某些工具初始化失败，创建一个最小的工具注册表
            logger.warn("🔧 创建最小工具注册表（仅包含基础工具）...");
            ToolRegistry minimalRegistry = new ToolRegistry(
                new AskHumanTool(),
                new TerminateTool()
            );
            
            logger.info("⚠️ 最小工具注册表创建完成，包含 {} 个工具", minimalRegistry.getToolCount());
            return minimalRegistry;
        }
    }
} 