package com.openmanus.agent.demo;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 演示context传递修复效果的示例类
 * 
 * 这个类展示了修复前后的差异：
 * 1. 修复前：所有agent的execute方法中context都为空
 * 2. 修复后：context能够正确传递给agent
 */
@Slf4j
@Component
public class ContextPassingDemo {

    /**
     * 演示修复前的问题
     */
    public void demonstrateProblemBefore() {
        log.info("=== 修复前的问题演示 ===");
        log.info("问题：AbstractAgentExecutor.execute()方法忽略了context参数");
        log.info("结果：所有继承AbstractAgentExecutor的agent都无法获得状态信息");
        
        // 模拟修复前的代码逻辑
        Object context = createSampleContext();
        log.info("传入的context: {}", context);
        
        // 修复前的逻辑（已修复）
        log.info("修复前：context被忽略，只使用toolExecutionRequest.arguments()");
        log.info("导致：agent内部无法访问状态信息，context永远为空");
    }

    /**
     * 演示修复后的效果
     */
    public void demonstrateFixAfter() {
        log.info("=== 修复后的效果演示 ===");
        log.info("修复：AbstractAgentExecutor.execute()现在正确处理context参数");
        
        // 模拟修复后的代码逻辑
        Object context = createSampleContext();
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("demo")
                .arguments("演示参数")
                .build();
        
        log.info("传入的context: {}", context);
        
        // 模拟修复后的逻辑
        Map<String, Object> initialState;
        if (context != null && context instanceof Map) {
            // 如果context是Map（来自AgentHandoff），使用它作为基础状态
            initialState = new HashMap<>((Map<String, Object>) context);
            log.info("✅ 来自AgentHandoff的调用，使用完整context: {}", initialState);
        } else {
            // 如果context为null（来自AgentToolbox），创建最小状态
            initialState = Map.of("messages", "用户消息");
            log.info("✅ 来自AgentToolbox的调用，创建最小状态: {}", initialState);
        }
        
        log.info("修复后：agent可以正确访问状态信息");
    }

    /**
     * 创建示例context
     */
    private Map<String, Object> createSampleContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("original_request", "用户的原始请求");
        context.put("cycle_count", 2);
        context.put("phase", "doing");
        context.put("execution_plan", "执行计划内容");
        return context;
    }

    /**
     * 演示不同调用场景
     */
    public void demonstrateCallScenarios() {
        log.info("=== 调用场景演示 ===");
        
        // 场景1：AgentHandoff调用
        log.info("场景1：AgentHandoff调用（context包含完整状态）");
        Map<String, Object> handoffContext = createSampleContext();
        simulateAgentCall("AgentHandoff", handoffContext);
        
        // 场景2：AgentToolbox调用
        log.info("场景2：AgentToolbox调用（context为null）");
        simulateAgentCall("AgentToolbox", null);
    }

    /**
     * 模拟agent调用
     */
    private void simulateAgentCall(String caller, Object context) {
        log.info("调用者: {}", caller);
        log.info("传入context: {}", context != null ? context.toString() : "null");
        
        if (context != null) {
            log.info("✅ Agent可以访问状态信息，进行状态管理");
            if (context instanceof Map) {
                Map<String, Object> state = (Map<String, Object>) context;
                log.info("  - 原始请求: {}", state.get("original_request"));
                log.info("  - 循环次数: {}", state.get("cycle_count"));
                log.info("  - 当前阶段: {}", state.get("phase"));
            }
        } else {
            log.info("⚠️  Context为null，但agent仍可正常工作（使用最小状态）");
        }
        log.info("---");
    }

    /**
     * 运行完整演示
     */
    public void runFullDemo() {
        log.info("🚀 开始Context传递修复演示");
        log.info("");
        
        demonstrateProblemBefore();
        log.info("");
        
        demonstrateFixAfter();
        log.info("");
        
        demonstrateCallScenarios();
        log.info("");
        
        log.info("✅ 演示完成！Context传递问题已修复");
    }
}
