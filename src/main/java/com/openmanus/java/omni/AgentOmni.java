package com.openmanus.java.omni;

import com.openmanus.java.agent.AbstractAgentExecutor;
import com.openmanus.java.marketplace.AgentMarketplace;
import com.openmanus.java.omni.tool.OmniToolCatalog;
import dev.langchain4j.data.message.SystemMessage;
import org.bsc.langgraph4j.GraphStateException;

/**
 * 一个多功能、自包含的 Agent 执行器。
 *
 * 它遵循与其他专用 Agent (如 AgentPayment) 相同的设计模式：
 * 1. 继承自 AbstractAgentExecutor，使其拥有一个内部的状态图（StateGraph）。
 * 2. 其 Builder 负责配置这个内部工作流所需的所有资源，包括 ChatModel 和它自己的一组工具。
 * 3. 当被更高阶的工作流（如 MultiAgentHandoffWorkflow）当作工具调用时，它会独立运行自己的工作流来完成任务。
 */
public class AgentOmni extends AbstractAgentExecutor<AgentOmni.Builder> {

    /**
     * 私有构造函数，确保通过 Builder 创建。
     * @param builder a {@link Builder} instance.
     * @throws GraphStateException if graph compilation fails.
     */
    private AgentOmni(Builder builder) throws GraphStateException {
        super(builder);
    }

    /**
     * 用于配置和构建 AgentOmni 的 Builder。
     */
    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {

        /**
         * Builder 的构造函数现在接收它完成任务所需的所有依赖。
         * @param omniToolCatalog 此 Agent 可用的工具集合。
         */
        public AgentOmni build(OmniToolCatalog omniToolCatalog) throws GraphStateException {
            // 1. 为主管 Agent 配置“名片”，告诉它这个工具是干什么的。
            this.name("manus_agent")
                    .description("一个多功能通用代理，擅长处理编程、文件操作、网页浏览和一般性问答。当其他专用代理无法处理时，应调用此代理。")
                    .singleParameter("用户的详细请求，将完整传递给多功能代理进行处理。");

            // 2. 配置其内部工作流的核心组件。
            //    这些方法都继承自 AbstractAgentExecutor.Builder
            this.systemMessage(SystemMessage.from(
                    "You are OpenManus, a powerful multi-functional AI assistant. " +
                            "You can browse the web, write and execute code, and interact with the file system. " +
                            "Analyze the user's request and use the available tools to achieve the goal."
            )).toolFromObject(omniToolCatalog.getTools().toArray(new Object[0]));
            return new AgentOmni(this);
        }
        public static AgentMarketplace.Builder builder() {
            return new AgentMarketplace.Builder();
        }

        public AgentOmni build() throws GraphStateException {
            return new AgentOmni(this);
        }
    }
}