package com.openmanus.java.agent;

import com.openmanus.java.model.Memory;
import com.openmanus.java.tool.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class ManusAgentTestConfig {

    @Bean
    @Primary
    public Memory testMemory() {
        return new Memory();
    }

    @Bean
    @Primary
    public ToolRegistry testToolRegistry() {
        // Create a test tool registry with only safe tools (no Docker dependencies)
        return new ToolRegistry(
            new AskHumanTool(),
            new TerminateTool(),
            new FileTool() // Using no-arg constructor to avoid sandbox dependencies
        );
    }
}