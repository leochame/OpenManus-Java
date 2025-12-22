package com.openmanus.agent.workflow;

import com.openmanus.agent.base.AgentHandoff;

import com.openmanus.agent.impl.executor.CodeAgent;
import com.openmanus.agent.impl.executor.FileAgent;
import com.openmanus.agent.impl.executor.SearchAgent;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 快思考工作流（Fast Thinking Workflow）
 * 
 * 这个工作流专注于快速响应，直接调用适当的Agent处理用户请求，不进行复杂的思考和反思过程。
 * 适用场景：
 * 1. 简单明确的任务
 * 2. 需要快速响应的场景
 * 3. 对话式交互
 * 
 * 与ThinkDoReflectWorkflow的区别：
 * - 快思考：直接执行，响应迅速，适合简单任务
 * - 慢思考：分析规划、执行、反思，适合复杂任务
 */
@Service
public class FastThinkWorkflow {

    private final AgentHandoff handoffExecutor;

    /**
     * 构造函数，由 Spring 自动注入 ChatLanguageModel。
     * @param chatModel 用于所有 Agent 的语言模型
     * @throws Exception 如果构建失败
     */
    public FastThinkWorkflow(ChatModel chatModel,
                             SearchAgent searchAgent,
                             CodeAgent codeAgent,
                             FileAgent fileAgent) throws Exception {


        // 4. 使用 AgentHandoff.builder() 构建快思考工作流
        this.handoffExecutor = AgentHandoff.builder()
                .chatModel(chatModel) // 为主管 Agent 设置大脑
                .systemMessage("""
                    你是一个快速响应型智能助手，专注于直接高效地解决用户问题。
                    
                    工作原则：
                    - 直接理解用户意图并快速响应
                    - 优先使用最合适的工具解决问题
                    - 简洁明了地提供答案，不需要过多解释
                    - 对于简单问题，直接回答
                    - 对于复杂问题，建议用户使用"思考-执行-反思"模式
                    
                    你可以使用以下工具：
                    - marketplace_agent：处理商品、服务相关查询
                    - payment_agent：处理支付、交易相关查询
                    - omni_agent：通用工具，可处理各类常见问题
                    
                    记住：你是"快思考"模式，专注于快速响应和简单问题解决。""")
                .agent(searchAgent) // 注册 SearchAgent 作为一个可用的子工具
                .agent(codeAgent)   // 注册 CodeAgent 作为一个可用的子工具
                .agent(fileAgent)   // 注册 FileAgent 作为一个可用的子工具
                .build(); // 构建主管 Agent
    }

    /**
     * 执行快思考工作流。
     * @param userInput 用户的初始输入
     * @return Agent 的最终响应
     */
    public CompletableFuture<String> execute(String userInput) {
        return CompletableFuture.supplyAsync(() -> executeSync(userInput));
    }

    /**
     * 同步执行快思考工作流
     * @param userInput 用户输入
     * @return 执行结果
     */
    public String executeSync(String userInput) {
        // 1. 将用户输入包装成一个 ToolExecutionRequest，以启动 AgentHandoff 执行器
        // AgentHandoff 本身被设计成一个工具，所以它的入口是 execute 方法
        String arguments = String.format("{\"context\": \"%s\"}", userInput);
        ToolExecutionRequest initialRequest = ToolExecutionRequest.builder()
                .name(handoffExecutor.name()) // 使用主管 Agent 自己的名字
                .arguments(arguments)
                .build();

        // 2. 直接调用我们自定义的执行逻辑
        return handoffExecutor.execute(initialRequest, UUID.randomUUID());
    }
}
