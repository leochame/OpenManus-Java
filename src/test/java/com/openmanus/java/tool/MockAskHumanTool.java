package com.openmanus.java.tool;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock版本的AskHumanTool，专门用于测试环境
 * 不会阻塞等待用户输入，而是返回预定义的响应
 */
public class MockAskHumanTool {

    private static final Logger log = LoggerFactory.getLogger(MockAskHumanTool.class);

    public static final String NAME = "ask_human";
    public static final String DESCRIPTION = "Use this tool to ask human for help.";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger callCount = new AtomicInteger(0);
    private String[] predefinedResponses = {
        "测试响应1：继续执行",
        "测试响应2：任务完成",
        "测试响应3：终止执行"
    };

    public MockAskHumanTool() {
        log.debug("创建MockAskHumanTool实例");
    }

    public MockAskHumanTool(String... responses) {
        this();
        if (responses != null && responses.length > 0) {
            this.predefinedResponses = responses;
        }
    }

    /**
     * 关闭资源
     */
    public void close() {
        try {
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
                if (!executor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            }
        } catch (Exception e) {
            log.warn("Error shutting down executor: {}", e.getMessage());
        }
    }

    @Tool(name = "ask_human", value = DESCRIPTION)
    public String execute(String inquire) {
        log.debug("Mock询问人类: {}", inquire);
        
        // 模拟短暂的处理时间
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 返回预定义的响应
        int count = callCount.getAndIncrement();
        String response = predefinedResponses[count % predefinedResponses.length];
        
        log.debug("Mock人类响应: {}", response);
        return response;
    }

    /**
     * 异步版本
     */
    public CompletableFuture<String> executeAsync(String inquire) {
        return CompletableFuture.supplyAsync(() -> execute(inquire), executor);
    }

    /**
     * 与原版兼容的方法
     */
    public String ask(String inquire) {
        return execute(inquire);
    }

    /**
     * 与原版兼容的方法
     */
    public String askQuestion(String inquire) {
        return execute(inquire);
    }

    /**
     * 带超时的版本（Mock版本不需要真正的超时）
     */
    public String askQuestionWithTimeout(String inquire, int timeoutSeconds) {
        return execute(inquire);
    }

    /**
     * 获取工具名称
     */
    public String getName() {
        return NAME;
    }

    /**
     * 获取工具描述
     */
    public String getDescription() {
        return DESCRIPTION;
    }

    /**
     * 设置预定义响应
     */
    public void setPredefinedResponses(String... responses) {
        if (responses != null && responses.length > 0) {
            this.predefinedResponses = responses;
        }
    }

    /**
     * 重置调用计数
     */
    public void resetCallCount() {
        callCount.set(0);
    }

    /**
     * 获取调用次数
     */
    public int getCallCount() {
        return callCount.get();
    }
} 