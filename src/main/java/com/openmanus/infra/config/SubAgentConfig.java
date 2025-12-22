package com.openmanus.infra.config;

import com.openmanus.agent.impl.executor.CodeAgent;
import com.openmanus.agent.impl.executor.FileAgent;
import com.openmanus.agent.impl.executor.SearchAgent;
import com.openmanus.agent.impl.reflection.ReflectionAgent;
import com.openmanus.agent.impl.thinker.ThinkingAgent;
import com.openmanus.agent.tool.BrowserTool;
import com.openmanus.agent.tool.FileTool;
import com.openmanus.agent.tool.PythonTool;
import com.openmanus.infra.monitoring.AgentExecutionTracker;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 子智能体配置类
 * 
 * 采用工厂模式配置Think-Do-Reflect工作流中的所有智能体实例
 * 确保各智能体之间的协作和依赖关系正确配置
 */
@Configuration
public class SubAgentConfig {

    /**
     * 思考智能体 - 负责任务分析和规划
     */
    @Bean
    public ThinkingAgent thinkingAgent(ChatModel chatModel, 
                                      AgentExecutionTracker agentExecutionTracker) {
        return ThinkingAgent.builder()
                .chatModel(chatModel)
                .agentExecutionTracker(agentExecutionTracker)
                .build();
    }

    /**
     * 搜索智能体 - 负责网络搜索和信息检索
     */
    @Bean
    public SearchAgent searchAgent(ChatModel chatModel, BrowserTool browserTool) {
        return SearchAgent.builder()
                .chatModel(chatModel)
                .browserTool(browserTool)
                .build();
    }

    /**
     * 代码智能体 - 负责代码执行和计算
     */
    @Bean
    public CodeAgent codeAgent(ChatModel chatModel, PythonTool pythonTool) {
        return CodeAgent.builder()
                .chatModel(chatModel)
                .pythonTool(pythonTool)
                .build();
    }

    /**
     * 文件智能体 - 负责文件操作
     */
    @Bean
    public FileAgent fileAgent(ChatModel chatModel, FileTool fileTool) {
        return FileAgent.builder()
                .chatModel(chatModel)
                .fileTool(fileTool)
                .build();
    }

    /**
     * 反思智能体 - 负责结果评估和质量保证
     */
    @Bean
    public ReflectionAgent reflectionAgent(ChatModel chatModel, 
                                          AgentExecutionTracker agentExecutionTracker) {
        return ReflectionAgent.builder()
                .chatModel(chatModel)
                .agentExecutionTracker(agentExecutionTracker)
                .build();
    }
}
