package com.openmanus.java.config;

import com.openmanus.java.model.Memory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot配置类，用于创建LLM相关的Bean
 */
@Configuration
public class LlmClientConfig {

    @Autowired
    private OpenManusProperties properties;


    @Bean
    public Memory memory() {
        return new Memory();
    }
}
