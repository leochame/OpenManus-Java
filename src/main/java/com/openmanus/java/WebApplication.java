package com.openmanus.java;

import com.openmanus.java.agent.ManusAgent;
import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.sandbox.SandboxClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Scanner;
import java.util.Map;

/**
 * OpenManus 主应用类
 * 基于 langchain4j 和 langgraph4j 的极简 AI Agent 框架
 */
@SpringBootApplication(scanBasePackages = "com.openmanus.java")
public class WebApplication {

    private static final Logger logger = LoggerFactory.getLogger(WebApplication.class);

    public static void main(String[] args) {
        logger.info("🚀 启动 OpenManus-Java");
        SpringApplication.run(WebApplication.class, args);
        logger.info("🎉 OpenManus-Java 启动成功！");
    }

    /**
     * 配置沙箱客户端
     */
    @Bean
    public SandboxClient sandboxClient(OpenManusProperties properties) {
        return new SandboxClient(properties);
    }

    /**
     * 命令行运行器 - 提供简单的交互式界面
     */
    @Bean
    public CommandLineRunner commandLineRunner(ManusAgent agent) {
        return args -> {
            if (args.length > 0 && args[0].equals("--cli")) {
                System.out.println("=== OpenManus CLI ===");
                System.out.println("输入 'quit' 退出");
                
                Scanner scanner = new Scanner(System.in);
                while (true) {
                    System.out.print("> ");
                    String input = scanner.nextLine().trim();
                    
                    if ("quit".equalsIgnoreCase(input)) {
                        break;
                    }
                    
                    if (!input.isEmpty()) {
                        try {
                            Map<String, Object> result = agent.chatWithCot(input);
                            System.out.println("回答: " + result.get("answer"));
                            System.out.println("推理过程: " + result.get("cot"));
                        } catch (Exception e) {
                            System.err.println("错误: " + e.getMessage());
                        }
                    }
                }
                
                System.out.println("再见！");
                System.exit(0);
            }
        };
    }
}