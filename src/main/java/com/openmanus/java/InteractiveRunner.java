package com.openmanus.java;

import com.openmanus.java.agent.ManusAgent;
import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.llm.LlmClient;
import com.openmanus.java.model.Memory;
import com.openmanus.java.tool.ToolRegistry;
import com.openmanus.java.tool.PythonTool;
import com.openmanus.java.tool.FileTool;
import com.openmanus.java.tool.AskHumanTool;
import com.openmanus.java.tool.TerminateTool;
import com.openmanus.java.tool.BashTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

/**
 * 交互式运行器 - 允许用户与OpenManus进行实时交互
 */
@Component
@Primary
public class InteractiveRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InteractiveRunner.class);

    @Autowired
    private OpenManusProperties properties;

    @Autowired
    private LlmClient llmClient;

    @Override
    public void run(String... args) throws Exception {
        try {
            log.info("🚀 启动 OpenManus Java 交互式版本");
            log.info("配置信息: 模型={}, API类型={}",
                    properties.getLlm().getDefaultLlm().getModel(),
                    properties.getLlm().getDefaultLlm().getApiType());

            // 初始化工具系统
            ToolRegistry toolRegistry = new ToolRegistry(
                    new PythonTool(properties),
                    new FileTool(properties),
                    new AskHumanTool(),
                    new TerminateTool(),
                    new BashTool(properties));

            log.info("🔧 工具系统初始化完成，可用工具: {}", toolRegistry.getAllTools());

            // 创建智能体
            Memory memory = new Memory();
            ManusAgent agent = new ManusAgent(llmClient, memory, properties, toolRegistry,
                    com.openmanus.java.model.ToolChoice.AUTO,
                    java.util.Set.of("terminate"));

            log.info("🤖 Manus智能体初始化完成");

            // 开始交互循环
            startInteractiveLoop(agent);

        } catch (Exception e) {
            log.error("❌ 启动失败: {}", e.getMessage(), e);
        }
    }

    private void startInteractiveLoop(ManusAgent agent) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("🎉 欢迎使用 OpenManus Java 版本!");
        System.out.println("💡 您可以输入任务，让AI助手帮您完成");
        System.out.println("📝 输入 'exit' 或 'quit' 退出程序");
        System.out.println("🔧 可用工具: Python执行、文件操作、命令行、人机交互等");
        System.out.println("=".repeat(60) + "\n");

        while (true) {
            System.out.print("👤 请输入您的任务 (或 'exit' 退出): ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                System.out.println("👋 感谢使用OpenManus，再见！");
                break;
            }

            if (input.isEmpty()) {
                System.out.println("⚠️ 请输入有效的任务描述");
                continue;
            }

            try {
                System.out.println("\n🤖 Manus正在处理您的请求...");
                System.out.println("-".repeat(40));

                // 使用run方法执行任务
                CompletableFuture<String> result = agent.run(input);
                String output = result.get();

                System.out.println("-".repeat(40));
                System.out.println("✅ 任务完成！");
                System.out.println("📋 执行结果: " + output);
                System.out.println();

            } catch (Exception e) {
                System.out.println("❌ 执行任务时出错: " + e.getMessage());
                log.error("Task execution error", e);
            }
        }

        scanner.close();
    }

    public static void main(String[] args) {
        // 设置Spring Boot属性，确保使用交互式运行器
        System.setProperty("spring.main.web-application-type", "none");
        SpringApplication.run(InteractiveRunner.class, args);
    }
}
