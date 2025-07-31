package com.openmanus.java.agent.tool;

import com.openmanus.java.agent.impl.thinker.ThinkingAgent;
import com.openmanus.java.agent.impl.executor.SearchAgent;
import com.openmanus.java.agent.impl.executor.CodeAgent;
import com.openmanus.java.agent.impl.executor.FileAgent;
import com.openmanus.java.agent.impl.reflection.ReflectionAgent;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.springframework.stereotype.Component;

/**
 * 智能体工具箱 - 将所有智能体包装为工具
 * 
 * 使用@Tool注解定义智能体接口，使SupervisorAgent能够通过工具调用的方式
 * 选择和调用合适的智能体，而不是通过硬编码的判断逻辑。
 */
@Component
public class AgentToolbox {

    private final ThinkingAgent thinkingAgent;
    private final SearchAgent searchAgent;
    private final CodeAgent codeAgent;
    private final FileAgent fileAgent;
    private final ReflectionAgent reflectionAgent;

    public AgentToolbox(
            ThinkingAgent thinkingAgent,
            SearchAgent searchAgent,
            CodeAgent codeAgent,
            FileAgent fileAgent,
            ReflectionAgent reflectionAgent) {
        this.thinkingAgent = thinkingAgent;
        this.searchAgent = searchAgent;
        this.codeAgent = codeAgent;
        this.fileAgent = fileAgent;
        this.reflectionAgent = reflectionAgent;
    }

    @Tool("分析任务并创建执行计划。当需要理解用户需求、分解复杂任务或制定执行策略时使用。")
    public String think(String prompt) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("think")
                .arguments(prompt)
                .build();
        return thinkingAgent.execute(request, null);
    }

    @Tool("执行信息检索任务。当需要搜索网页信息、获取在线内容或查找特定资料时使用。")
    public String search(String query) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("search")
                .arguments(query)
                .build();
        return searchAgent.execute(request, null);
    }

    @Tool("执行代码相关任务。当需要运行Python代码、进行数据分析、计算或编程任务时使用。")
    public String executeCode(String codeTask) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("execute_code")
                .arguments(codeTask)
                .build();
        return codeAgent.execute(request, null);
    }

    @Tool("执行文件操作。当需要读取、写入、管理文件或处理文件内容时使用。")
    public String handleFile(String fileOperation) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("handle_file")
                .arguments(fileOperation)
                .build();
        return fileAgent.execute(request, null);
    }

    @Tool("评估执行结果并决定是否需要继续改进。用于判断任务完成度和提供改进建议。")
    public String reflect(String executionResult) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("reflect")
                .arguments(executionResult)
                .build();
        return reflectionAgent.execute(request, null);
    }
}
