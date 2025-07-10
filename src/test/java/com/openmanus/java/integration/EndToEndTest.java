package com.openmanus.java.integration;

import com.openmanus.java.agent.ManusAgent;
import com.openmanus.java.config.LangGraphStudioConfig;
import com.openmanus.java.controller.AgentController;
import com.openmanus.java.memory.MemoryTool;
import com.openmanus.java.tool.BrowserTool;
import com.openmanus.java.tool.FileTool;
import com.openmanus.java.tool.PythonTool;
import com.openmanus.java.tool.ReflectionTool;
import com.openmanus.java.nodes.ThinkNode;
import com.openmanus.java.nodes.ActNode;
import com.openmanus.java.nodes.ObserveNode;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * OpenManus Java 端到端测试
 * 
 * 测试完整的系统功能，包括：
 * - Agent 完整推理流程
 * - 工具集成和调用
 * - StateGraph 工作流执行
 * - 错误处理和恢复
 * - 性能和稳定性
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("OpenManus Java 端到端测试")
public class EndToEndTest {
    
    private static final Logger logger = LoggerFactory.getLogger(EndToEndTest.class);
    
    private ChatModel chatModel;
    private PythonTool pythonTool;
    private FileTool fileTool;
    private BrowserTool browserTool;
    private ReflectionTool reflectionTool;
    private MemoryTool memoryTool;
    private ManusAgent manusAgent;
    private AgentController agentController;
    
    @BeforeEach
    void setUp() {
        logger.info("设置端到端测试环境...");
        
        // 创建Mock对象
        chatModel = Mockito.mock(ChatModel.class);
        pythonTool = Mockito.mock(PythonTool.class);
        fileTool = Mockito.mock(FileTool.class);
        browserTool = Mockito.mock(BrowserTool.class);
        reflectionTool = Mockito.mock(ReflectionTool.class);
        memoryTool = Mockito.mock(MemoryTool.class);
        
        // 设置Mock行为
        setupMockBehaviors();
        
        // 创建节点组件
        ThinkNode thinkNode = new ThinkNode(chatModel);
        ActNode actNode = new ActNode(chatModel, pythonTool, fileTool, browserTool);
        ObserveNode observeNode = new ObserveNode(chatModel);
        
        // 创建ManusAgent实例
        manusAgent = new ManusAgent(chatModel, pythonTool, fileTool, browserTool, reflectionTool,
                                  thinkNode, actNode, observeNode);
        
        // 创建AgentController实例
        agentController = new AgentController();
        // 使用反射设置manusAgent字段
        try {
            java.lang.reflect.Field field = AgentController.class.getDeclaredField("manusAgent");
            field.setAccessible(true);
            field.set(agentController, manusAgent);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject manusAgent", e);
        }
        
        logger.info("端到端测试环境设置完成");
    }
    
    private void setupMockBehaviors() {
        // 设置ChatModel Mock行为
        when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenReturn(dev.langchain4j.model.chat.response.ChatResponse.builder()
                    .aiMessage(dev.langchain4j.data.message.AiMessage.from("这是一个模拟的AI响应"))
                    .build());
        
        // 设置PythonTool Mock行为
        when(pythonTool.executePython(any(String.class))).thenReturn("Python代码执行成功");
        
        // 设置FileTool Mock行为
        when(fileTool.readFile(any(String.class))).thenReturn("文件内容读取成功");
        when(fileTool.writeFile(any(String.class), any(String.class))).thenReturn("文件写入成功");
        
        // 设置BrowserTool Mock行为
        when(browserTool.searchWeb(any(String.class))).thenReturn("搜索结果：找到相关信息");
        
        // 设置ReflectionTool Mock行为
        when(reflectionTool.reflectOnTask(any(String.class))).thenReturn("反思：任务执行良好");
        
        // 设置MemoryTool Mock行为
        when(memoryTool.storeMemory(any(String.class), any(String.class), any(Double.class), any(String.class))).thenReturn("记忆保存成功");
        when(memoryTool.retrieveMemory(any(String.class), any(Integer.class), any(Double.class))).thenReturn("检索到相关记忆");
    }
    
    @Test
    @DisplayName("测试完整的数学计算场景")
    void testMathCalculationScenario() {
        logger.info("开始测试数学计算场景...");
        
        String userMessage = "请计算 15 * 23 + 47 的结果，并解释计算过程";
        
        // 执行完整的推理流程
        Map<String, Object> result = manusAgent.chatWithCot(userMessage);
        
        // 验证结果
        assertNotNull(result, "结果不应为空");
        assertTrue(result.containsKey("answer"), "结果应包含答案");
        assertTrue(result.containsKey("reasoning_steps"), "结果应包含推理步骤");
        assertTrue(result.containsKey("iteration_count"), "结果应包含迭代次数");
        
        String answer = (String) result.get("answer");
        assertNotNull(answer, "答案不应为空");
        assertFalse(answer.trim().isEmpty(), "答案不应为空字符串");
        
        logger.info("数学计算场景测试完成，答案: {}", answer);
    }
    
    @Test
    @DisplayName("测试文件操作场景")
    void testFileOperationScenario() {
        logger.info("开始测试文件操作场景...");
        
        String userMessage = "请创建一个名为test.txt的文件，写入'Hello World'，然后读取并显示内容";
        
        // 执行完整的推理流程
        Map<String, Object> result = manusAgent.chatWithCot(userMessage);
        
        // 验证结果
        assertNotNull(result, "结果不应为空");
        assertTrue(result.containsKey("answer"), "结果应包含答案");
        assertTrue(result.containsKey("tool_calls"), "结果应包含工具调用记录");
        
        String answer = (String) result.get("answer");
        assertNotNull(answer, "答案不应为空");
        
        logger.info("文件操作场景测试完成，答案: {}", answer);
    }
    
    @Test
    @DisplayName("测试网络搜索场景")
    void testWebSearchScenario() {
        logger.info("开始测试网络搜索场景...");
        
        String userMessage = "请搜索关于人工智能最新发展的信息，并总结主要趋势";
        
        // 执行完整的推理流程
        Map<String, Object> result = manusAgent.chatWithCot(userMessage);
        
        // 验证结果
        assertNotNull(result, "结果不应为空");
        assertTrue(result.containsKey("answer"), "结果应包含答案");
        assertTrue(result.containsKey("observations"), "结果应包含观察记录");
        
        String answer = (String) result.get("answer");
        assertNotNull(answer, "答案不应为空");
        
        logger.info("网络搜索场景测试完成，答案: {}", answer);
    }
    
    @Test
    @DisplayName("测试复杂推理场景")
    void testComplexReasoningScenario() {
        logger.info("开始测试复杂推理场景...");
        
        String userMessage = "请分析以下问题：如果一个班级有30名学生，其中60%是女生，" +
                           "女生中有75%喜欢数学，男生中有50%喜欢数学，那么整个班级中有多少学生喜欢数学？" +
                           "请详细说明计算过程。";
        
        // 执行完整的推理流程
        Map<String, Object> result = manusAgent.chatWithCot(userMessage);
        
        // 验证结果
        assertNotNull(result, "结果不应为空");
        assertTrue(result.containsKey("answer"), "结果应包含答案");
        assertTrue(result.containsKey("cot"), "结果应包含思维链");
        assertTrue(result.containsKey("reasoning_steps"), "结果应包含推理步骤");
        
        String answer = (String) result.get("answer");
        assertNotNull(answer, "答案不应为空");
        
        // 验证推理步骤数量
        Object reasoningSteps = result.get("reasoning_steps");
        assertNotNull(reasoningSteps, "推理步骤不应为空");
        
        logger.info("复杂推理场景测试完成，答案: {}", answer);
    }
    
    @Test
    @DisplayName("测试错误处理和恢复")
    void testErrorHandlingAndRecovery() {
        logger.info("开始测试错误处理和恢复...");
        
        // 模拟工具调用失败
        when(pythonTool.executePython(any(String.class)))
            .thenThrow(new RuntimeException("Python执行失败"))
            .thenReturn("Python代码执行成功"); // 第二次调用成功
        
        String userMessage = "请执行Python代码计算1+1";
        
        // 执行推理流程
        Map<String, Object> result = manusAgent.chatWithCot(userMessage);
        
        // 验证结果
        assertNotNull(result, "结果不应为空");
        assertTrue(result.containsKey("answer"), "结果应包含答案");
        
        String answer = (String) result.get("answer");
        assertNotNull(answer, "答案不应为空");
        
        logger.info("错误处理和恢复测试完成，答案: {}", answer);
    }
    
    @Test
    @DisplayName("测试多轮对话场景")
    void testMultiTurnConversationScenario() {
        logger.info("开始测试多轮对话场景...");
        
        // 第一轮对话
        String firstMessage = "请帮我创建一个购物清单";
        Map<String, Object> firstResult = manusAgent.chatWithCot(firstMessage);
        
        assertNotNull(firstResult, "第一轮结果不应为空");
        assertTrue(firstResult.containsKey("answer"), "第一轮结果应包含答案");
        
        // 第二轮对话
        String secondMessage = "请在购物清单中添加苹果和香蕉";
        Map<String, Object> secondResult = manusAgent.chatWithCot(secondMessage);
        
        assertNotNull(secondResult, "第二轮结果不应为空");
        assertTrue(secondResult.containsKey("answer"), "第二轮结果应包含答案");
        
        // 第三轮对话
        String thirdMessage = "请计算购物清单中水果的总数量";
        Map<String, Object> thirdResult = manusAgent.chatWithCot(thirdMessage);
        
        assertNotNull(thirdResult, "第三轮结果不应为空");
        assertTrue(thirdResult.containsKey("answer"), "第三轮结果应包含答案");
        
        logger.info("多轮对话场景测试完成");
    }
    
    @Test
    @DisplayName("测试性能和稳定性")
    void testPerformanceAndStability() {
        logger.info("开始测试性能和稳定性...");
        
        String userMessage = "请计算2+2";
        int testRounds = 5;
        
        for (int i = 0; i < testRounds; i++) {
            long startTime = System.currentTimeMillis();
            
            Map<String, Object> result = manusAgent.chatWithCot(userMessage);
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // 验证结果
            assertNotNull(result, "第" + (i + 1) + "轮结果不应为空");
            assertTrue(result.containsKey("answer"), "第" + (i + 1) + "轮结果应包含答案");
            
            // 验证性能（应在合理时间内完成）
            assertTrue(duration < 30000, "第" + (i + 1) + "轮执行时间应少于30秒，实际: " + duration + "ms");
            
            logger.info("第{}轮测试完成，耗时: {}ms", i + 1, duration);
        }
        
        logger.info("性能和稳定性测试完成");
    }
    
    @Test
    @DisplayName("测试Agent信息获取")
    void testAgentInfoRetrieval() {
        logger.info("开始测试Agent信息获取...");
        
        Map<String, Object> agentInfo = manusAgent.getAgentInfo();
        
        assertNotNull(agentInfo, "Agent信息不应为空");
        assertTrue(agentInfo.containsKey("architecture"), "应包含架构信息");
        assertTrue(agentInfo.containsKey("model"), "应包含模型信息");
        assertTrue(agentInfo.containsKey("nodes"), "应包含节点信息");
        assertTrue(agentInfo.containsKey("tools"), "应包含工具信息");
        
        assertEquals("LangGraph4j StateGraph", agentInfo.get("architecture"), "架构应为LangGraph4j StateGraph");
        
        logger.info("Agent信息获取测试完成: {}", agentInfo);
    }
    
    @Test
    @DisplayName("测试系统集成")
    void testSystemIntegration() {
        logger.info("开始测试系统集成...");
        
        // 测试所有组件是否正确初始化
        assertNotNull(manusAgent, "ManusAgent不应为空");
        assertNotNull(agentController, "AgentController不应为空");
        
        // 测试Agent信息
        Map<String, Object> agentInfo = manusAgent.getAgentInfo();
        assertNotNull(agentInfo, "Agent信息不应为空");
        
        // 测试基本功能
        String testMessage = "Hello, OpenManus!";
        Map<String, Object> result = manusAgent.chatWithCot(testMessage);
        
        assertNotNull(result, "结果不应为空");
        assertTrue(result.containsKey("answer"), "结果应包含答案");
        
        logger.info("系统集成测试完成");
    }
}