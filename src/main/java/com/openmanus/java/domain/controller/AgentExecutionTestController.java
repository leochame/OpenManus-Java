package com.openmanus.java.domain.controller;

import com.openmanus.java.agent.impl.thinker.ThinkingAgent;
import com.openmanus.java.agent.impl.executor.SearchAgent;
import com.openmanus.java.agent.impl.executor.CodeAgent;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.bsc.langgraph4j.GraphStateException;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent执行测试控制器
 * 用于验证Agent的execute方法调用机制
 */
@RestController
@RequestMapping("/api/agent-execution-test")
@CrossOrigin(origins = "*")
@Slf4j
public class AgentExecutionTestController {
    
    @Autowired
    private ChatModel chatModel;
    
    /**
     * 测试直接调用Agent的execute方法
     */
    @PostMapping("/direct-call")
    public ResponseEntity<Map<String, Object>> testDirectCall(@RequestBody Map<String, String> request) {
        String input = request.getOrDefault("input", "测试输入");
        Map<String, Object> results = new HashMap<>();
        
        try {
            // 创建Agent实例
            ThinkingAgent thinkingAgent = ThinkingAgent.builder()
                    .chatModel(chatModel)
                    .build();
            
            SearchAgent searchAgent = SearchAgent.builder()
                    .chatModel(chatModel)
                    .build();
            
            CodeAgent codeAgent = CodeAgent.builder()
                    .chatModel(chatModel)
                    .build();
            
            // 创建ToolExecutionRequest
            ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                    .name("test")
                    .arguments(input)
                    .build();
            
            // 直接调用execute方法（context为null）
            log.info("=== 开始直接调用Agent execute方法 ===");
            
            String thinkingResult = thinkingAgent.execute(toolRequest, null);
            log.info("ThinkingAgent直接调用结果: {}", thinkingResult);
            results.put("thinkingAgent_direct", thinkingResult);
            
            String searchResult = searchAgent.execute(toolRequest, null);
            log.info("SearchAgent直接调用结果: {}", searchResult);
            results.put("searchAgent_direct", searchResult);
            
            String codeResult = codeAgent.execute(toolRequest, null);
            log.info("CodeAgent直接调用结果: {}", codeResult);
            results.put("codeAgent_direct", codeResult);
            
            // 使用context调用execute方法
            log.info("=== 开始使用context调用Agent execute方法 ===");
            Map<String, Object> context = new HashMap<>();
            context.put("test", "context_data");
            
            String thinkingResultWithContext = thinkingAgent.execute(toolRequest, context);
            log.info("ThinkingAgent使用context调用结果: {}", thinkingResultWithContext);
            results.put("thinkingAgent_context", thinkingResultWithContext);
            
            results.put("success", true);
            results.put("message", "Agent execute方法测试完成");
            
        } catch (Exception e) {
            log.error("测试Agent execute方法时出错", e);
            results.put("success", false);
            results.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(results);
    }
    
    /**
     * 测试Agent作为Tool的调用机制
     */
    @PostMapping("/tool-call")
    public ResponseEntity<Map<String, Object>> testToolCall(@RequestBody Map<String, String> request) {
        String input = request.getOrDefault("input", "测试工具调用");
        Map<String, Object> results = new HashMap<>();
        
        try {
            // 创建Agent实例
            ThinkingAgent thinkingAgent = ThinkingAgent.builder()
                    .chatModel(chatModel)
                    .build();
            
            // 获取Agent的Tool表示
            var toolEntry = thinkingAgent.asTool();
            var toolSpec = toolEntry.getKey();
            var toolExecutor = toolEntry.getValue();
            
            log.info("=== Agent作为Tool的信息 ===");
            log.info("Tool名称: {}", toolSpec.name());
            log.info("Tool描述: {}", toolSpec.description());
            log.info("Tool参数: {}", toolSpec.parameters());
            log.info("ToolExecutor类型: {}", toolExecutor.getClass().getName());
            log.info("ToolExecutor是否为Agent本身: {}", toolExecutor == thinkingAgent);
            
            // 通过ToolExecutor调用
            ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                    .name(toolSpec.name())
                    .arguments(input)
                    .build();
            
            String result = toolExecutor.execute(toolRequest, null);
            log.info("通过ToolExecutor调用结果: {}", result);
            
            results.put("success", true);
            results.put("toolName", toolSpec.name());
            results.put("toolDescription", toolSpec.description());
            results.put("executorClass", toolExecutor.getClass().getName());
            results.put("isAgentItself", toolExecutor == thinkingAgent);
            results.put("result", result);
            
        } catch (Exception e) {
            log.error("测试Agent作为Tool时出错", e);
            results.put("success", false);
            results.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(results);
    }
    
    /**
     * 分析Agent的继承结构
     */
    @GetMapping("/analyze-structure")
    public ResponseEntity<Map<String, Object>> analyzeAgentStructure() {
        Map<String, Object> results = new HashMap<>();
        
        try {
            // 创建不同类型的Agent
            ThinkingAgent thinkingAgent = ThinkingAgent.builder()
                    .chatModel(chatModel)
                    .build();
            
            SearchAgent searchAgent = SearchAgent.builder()
                    .chatModel(chatModel)
                    .build();
            
            // 分析继承结构
            results.put("ThinkingAgent", analyzeAgent(thinkingAgent));
            results.put("SearchAgent", analyzeAgent(searchAgent));
            
            results.put("success", true);
            
        } catch (Exception e) {
            log.error("分析Agent结构时出错", e);
            results.put("success", false);
            results.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(results);
    }
    
    private Map<String, Object> analyzeAgent(Object agent) {
        Map<String, Object> info = new HashMap<>();
        
        Class<?> clazz = agent.getClass();
        info.put("className", clazz.getName());
        info.put("superClass", clazz.getSuperclass().getName());
        
        // 获取所有接口
        Class<?>[] interfaces = clazz.getInterfaces();
        String[] interfaceNames = new String[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            interfaceNames[i] = interfaces[i].getName();
        }
        info.put("interfaces", interfaceNames);
        
        // 检查是否实现了ToolExecutor
        info.put("implementsToolExecutor", 
                java.util.Arrays.stream(interfaces)
                        .anyMatch(i -> i.getName().contains("ToolExecutor")));
        
        return info;
    }
    
    /**
     * 测试工具调用机制的深层分析
     */
    @PostMapping("/deep-analysis")
    public ResponseEntity<Map<String, Object>> deepAnalysis(@RequestBody Map<String, String> request) {
        String input = request.getOrDefault("input", "深度分析工具调用机制");
        Map<String, Object> results = new HashMap<>();

        try {
            // 创建一个自定义的工具对象来测试
            Object customTool = new Object() {
                public String testMethod(String input) {
                    log.info("🔥🔥🔥 customTool.testMethod 被调用了！🔥🔥🔥");
                    log.info("Input: {}", input);
                    return "Custom tool result: " + input;
                }
            };

            // 创建AgentExecutor并添加工具
            var agentExecutor = org.bsc.langgraph4j.agentexecutor.AgentExecutor.builder()
                    .chatModel(chatModel)
                    .toolsFromObject(customTool)
                    .build()
                    .compile();

            log.info("=== 开始调用agentExecutor.invoke ===");

            // 调用agentExecutor
            var initialState = Map.of("messages", dev.langchain4j.data.message.UserMessage.from("请调用testMethod工具，参数是：" + input));
            var result = agentExecutor.invoke(initialState);

            log.info("=== agentExecutor.invoke执行完成 ===");

            String finalResponse = result
                    .map(org.bsc.langgraph4j.agentexecutor.AgentExecutor.State::finalResponse)
                    .flatMap(opt -> opt)
                    .orElse("无响应");

            results.put("success", true);
            results.put("result", finalResponse);
            results.put("message", "深度分析完成，请查看日志");

        } catch (Exception e) {
            log.error("深度分析时出错", e);
            results.put("success", false);
            results.put("error", e.getMessage());
        }

        return ResponseEntity.ok(results);
    }

    /**
     * 测试AgentHandoff的实际执行机制
     */
    @PostMapping("/test-handoff")
    public ResponseEntity<Map<String, Object>> testAgentHandoff(@RequestBody Map<String, String> request) {
        String input = request.getOrDefault("input", "测试AgentHandoff执行机制");
        Map<String, Object> results = new HashMap<>();

        try {
            // 创建一个简单的测试Agent，重写execute方法添加日志
            ThinkingAgent testAgent = new ThinkingAgent.Builder() {
                @Override
                public ThinkingAgent build() throws GraphStateException {
                    return new ThinkingAgent(this) {
                        @Override
                        public String execute(ToolExecutionRequest request, Object context) {
                            log.info("=== TestAgent.execute被调用了！===");
                            log.info("Request: {}", request.arguments());
                            log.info("Context: {}", context);
                            return super.execute(request, context);
                        }
                    };
                }
            }.chatModel(chatModel).build();

            // 使用AgentHandoff构建工作流
            var handoffExecutor = com.openmanus.java.agent.base.AgentHandoff.builder()
                    .chatModel(chatModel)
                    .agent(testAgent)
                    .build()
                    .compile();

            log.info("=== 开始调用handoffExecutor.invoke ===");

            // 调用handoffExecutor
            var initialState = Map.of("messages", dev.langchain4j.data.message.UserMessage.from(input));
            var result = handoffExecutor.invoke(initialState);

            log.info("=== handoffExecutor.invoke执行完成 ===");

            String finalResponse = result
                    .map(org.bsc.langgraph4j.agentexecutor.AgentExecutor.State::finalResponse)
                    .flatMap(opt -> opt)
                    .orElse("无响应");

            results.put("success", true);
            results.put("result", finalResponse);
            results.put("message", "AgentHandoff测试完成，请查看日志");

        } catch (Exception e) {
            log.error("测试AgentHandoff时出错", e);
            results.put("success", false);
            results.put("error", e.getMessage());
        }

        return ResponseEntity.ok(results);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = Map.of(
            "status", "healthy",
            "service", "Agent Execution Test",
            "timestamp", System.currentTimeMillis()
        );
        return ResponseEntity.ok(status);
    }
}
