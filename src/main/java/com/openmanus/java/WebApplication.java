package com.openmanus.java;

import com.openmanus.java.agent.ManusAgent;
import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.llm.LlmClient;
import com.openmanus.java.model.Memory;
import com.openmanus.java.sandbox.SandboxClient;
import com.openmanus.java.tool.BrowserTool;
import com.openmanus.java.tool.FileTool;
import com.openmanus.java.tool.PythonTool;
import com.openmanus.java.tool.ReflectionTool;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import java.util.Scanner;
import java.util.Map;

/**
 * OpenManus 主应用类
 * 基于 langchain4j 和 langgraph4j 的极简 AI Agent 框架
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.openmanus.java")
public class WebApplication {

    private static final Logger logger = LoggerFactory.getLogger(WebApplication.class);

    public static void main(String[] args) {
        logger.info("🚀 启动 OpenManus-Java");
        SpringApplication.run(WebApplication.class, args);
        logger.info("🎉 OpenManus-Java 启动成功！");
    }





    /**
     * 配置 Python 工具
     */
    @Bean
    public PythonTool pythonTool() {
        return new PythonTool();
    }

    /**
     * 配置浏览器工具
     */
    @Bean
    public BrowserTool browserTool() {
        return new BrowserTool();
    }

    /**
     * 配置文件工具
     */
    @Bean
    public FileTool fileTool() {
        return new FileTool();
    }

    /**
     * 配置反思工具
     */
    @Bean
    public ReflectionTool reflectionTool() {
        return new ReflectionTool();
    }

    /**
     * 配置沙箱客户端
     */
    @Bean
    public SandboxClient sandboxClient(OpenManusProperties properties) {
        return new SandboxClient(properties);
    }

    /**
     * 配置 ManusAgent
     */
    @Bean
    public ManusAgent manusAgent(ChatModel chatModel, 
                                PythonTool pythonTool,
                                FileTool fileTool, 
                                BrowserTool browserTool,
                                ReflectionTool reflectionTool) {
        return new ManusAgent(chatModel, pythonTool, fileTool, browserTool, reflectionTool);
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