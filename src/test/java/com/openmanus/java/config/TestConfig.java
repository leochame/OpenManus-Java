package com.openmanus.java.config;

import com.openmanus.java.model.Memory;
import com.openmanus.java.tool.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestConfig {

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
            new MockAskHumanTool(),
            new TerminateTool()
        );
    }
    
    /**
     * 提供一个空的CommandLineRunner来替代InteractiveRunner
     * 防止测试时启动交互式界面
     */
    @Bean
    @Primary
    public CommandLineRunner testCommandLineRunner() {
        return args -> {
            // 在测试环境中不执行任何操作
            System.out.println("Test environment: CommandLineRunner disabled");
        };
    }
}
