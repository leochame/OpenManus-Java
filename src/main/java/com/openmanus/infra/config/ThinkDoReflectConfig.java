package com.openmanus.infra.config;

import com.openmanus.agent.impl.thinker.ThinkingAgent;
import com.openmanus.agent.impl.executor.SearchAgent;
import com.openmanus.agent.impl.executor.CodeAgent;
import com.openmanus.agent.impl.executor.FileAgent;
import com.openmanus.agent.impl.reflection.ReflectionAgent;
import com.openmanus.agent.tool.BrowserTool;
import com.openmanus.agent.tool.PythonTool;
import com.openmanus.agent.tool.FileTool;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.GraphStateException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Think-Do-Reflect系统配置类
 * 
 * 负责创建和配置所有智能体实例，确保它们能够正确协作。
 */
@Configuration
public class ThinkDoReflectConfig {

    /**
     * 创建思考智能体
     */
    @Bean
    public ThinkingAgent thinkingAgent(ChatModel chatModel) throws GraphStateException {
        return ThinkingAgent.builder()
                .chatModel(chatModel)
                .build();
    }

    /**
     * 创建搜索智能体
     */
    @Bean
    public SearchAgent searchAgent(ChatModel chatModel, BrowserTool browserTool) throws GraphStateException {
        return SearchAgent.builder()
                .chatModel(chatModel)
                .browserTool(browserTool)
                .build();
    }

    /**
     * 创建代码智能体
     */
    @Bean
    public CodeAgent codeAgent(ChatModel chatModel, PythonTool pythonTool) throws GraphStateException {
        return CodeAgent.builder()
                .chatModel(chatModel)
                .pythonTool(pythonTool)
                .build();
    }

    /**
     * 创建文件智能体
     */
    @Bean
    public FileAgent fileAgent(ChatModel chatModel, FileTool fileTool) throws GraphStateException {
        return FileAgent.builder()
                .chatModel(chatModel)
                .fileTool(fileTool)
                .build();
    }

    /**
     * 创建反思智能体
     */
    @Bean
    public ReflectionAgent reflectionAgent(ChatModel chatModel) throws GraphStateException {
        return ReflectionAgent.builder()
                .chatModel(chatModel)
                .build();
    }
}
