package com.openmanus.agent.impl.omni;

import com.openmanus.agent.base.AbstractAgentExecutor;
import com.openmanus.agent.tool.OmniToolCatalog;
import dev.langchain4j.data.message.SystemMessage;
import org.bsc.langgraph4j.GraphStateException;

import java.util.List;

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
     * 用于配置和构建 AgentOmni 的 Builder。
     */
    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {

        private OmniToolCatalog omniToolCatalog;

        /**
         * 设置工具catalog
         */
        public Builder toolCatalog(OmniToolCatalog omniToolCatalog) {
            this.omniToolCatalog = omniToolCatalog;
            return this;
        }

        /**
         * Builder 的构造函数现在接收它完成任务所需的所有依赖。
         */
        public AgentOmni build() throws GraphStateException {
            // 1. 为主管 Agent 配置"名片"，告诉它这个工具是干什么的。
            this.name("manus_agent")
                    .description("一个多功能通用代理，擅长处理文件操作、网页浏览、Python代码执行和一般性问答。可以搜索网页、获取网页内容、执行Python代码、读写文件等。当需要搜索网页信息、执行代码或文件操作时，应调用此代理。")
                    .singleParameter("用户的详细请求，将完整传递给多功能代理进行处理。")
                    .systemMessage(SystemMessage.from("""
                            You are OpenManus, a powerful multi-functional AI assistant with access to web browsing, Python execution, and file system tools.
                            
                            Available tools:
                            - Web browsing: searchWeb(query) to search the internet, browseWeb(url) to visit specific pages
                            - Python execution: executePython(code) to run Python code  
                            - File operations: readFile, writeFile, listDirectory, etc.
                            
                            When the user asks for web searches, current information, or wants to search for something online, use the searchWeb tool.
                            When the user provides URLs to visit, use the browseWeb tool.
                            When the user needs code execution or calculations, use the executePython tool.
                            When the user needs file operations, use the appropriate file tools.
                            
                            Always analyze the user's request carefully and use the most appropriate tool to achieve the goal.
                            """));
            
            // 只有在提供了工具catalog时才注册工具
            if (omniToolCatalog != null) {
                List<Object> tools = omniToolCatalog.getTools();
                this.toolsFromObjects(tools.toArray(new Object[0]));
            }
            
            return new AgentOmni(this);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }

    private AgentOmni(Builder builder) throws GraphStateException {
        super(builder);
    }
}
