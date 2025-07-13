package com.openmanus.java.workflow;

import com.openmanus.java.agent.AgentHandoff;

import com.openmanus.java.marketplace.AgentMarketplace;
import com.openmanus.java.marketplace.AgentPayment;
import com.openmanus.java.omni.AgentOmni;
import com.openmanus.java.omni.tool.OmniToolCatalog;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 多 Agent 协作工作流。
 * 这个类是 agents-handoff 模式的核心，它负责：
 * 1. 初始化所有参与的子 Agent。
 * 2. 构建一个总控 Agent (Supervisor)，并将子 Agent 作为其工具。
 * 3. 编译并执行整个图流程。
 */
@Service
public class MultiAgentHandoffWorkflow {

    private final CompiledGraph<AgentExecutor.State> handoffExecutor;

    /**
     * 构造函数，由 Spring 自动注入 ChatLanguageModel。
     * @param chatModel 用于所有 Agent 的语言模型
     * @throws Exception 如果构建失败
     */
    public MultiAgentHandoffWorkflow(ChatModel chatModel, OmniToolCatalog omniToolCatalog) throws Exception {

        // 1. 创建 MarketplaceAgent 实例
        AgentMarketplace agentMarketplace = new AgentMarketplace.Builder()
                .chatModel(chatModel)
                .build();

        // 2. 创建 PaymentAgent 实例
        AgentPayment agentPayment = new AgentPayment.Builder()
                .chatModel(chatModel)
                .build();

        AgentOmni agentOmni = new AgentOmni.Builder()
                .chatModel(chatModel).
                build(omniToolCatalog);

        // 3. 使用 AgentHandoff.Builder 构建总控工作流
        // AgentHandoff 是一个特殊的 Agent，它的工具就是其他 Agent。
        // 它会根据用户的输入和对话历史，决定调用哪个子 Agent 来完成任务。
        this.handoffExecutor = new AgentHandoff.Builder()
                .chatModel(chatModel)
                .agent(agentMarketplace) // 将 marketplaceAgent 作为工具添加
                .agent(agentPayment)     // 将 paymentAgent 作为工具添加
                .agent(agentOmni)
                .build()
                .compile(); // 编译图，使其可执行
    }

    /**
     * 执行多 Agent 协作流程。
     * @param userInput 用户的初始输入
     * @return Agent 的最终响应
     */
    public CompletableFuture<String> execute(String userInput) {
        Map<String, Object> inputState = Map.of("messages", UserMessage.from(userInput));
        return CompletableFuture.supplyAsync(()-> handoffExecutor.invoke(inputState)).thenApply(
                response -> {
                    return response.flatMap(AgentExecutor.State::finalResponse)
                            .orElse("No response from agent.");
                });
        // 将用户输入包装成 Map，这是图流程的输入格式
    }
}
