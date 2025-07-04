package com.openmanus.java.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot配置类，启用配置属性绑定
 */
@Configuration
@EnableConfigurationProperties(OpenManusProperties.class)
public class ConfigurationConfig {
}