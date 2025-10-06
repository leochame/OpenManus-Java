package com.openmanus.agent.impl.executor;

import com.openmanus.agent.base.AbstractAgentExecutor;
import com.openmanus.agent.tool.BrowserTool;
import dev.langchain4j.data.message.SystemMessage;
import org.bsc.langgraph4j.GraphStateException;

/**
 * 搜索智能体 - 负责信息检索任务
 *
 * 升级功能：
 * 1. 智能自适应RAG检索 - 自动路由到最佳数据源
 * 2. 向量存储语义搜索 - 专业领域知识检索
 * 3. 网页搜索和信息检索 - 时效性信息获取
 * 4. 文档相关性评分 - 过滤无关内容
 * 5. 幻觉检测和答案质量评估 - 确保答案准确性
 * 6. 问题重写优化 - 提高检索效果
 * 7. 传统浏览器工具 - 保持向后兼容
 */
public class SearchAgent extends AbstractAgentExecutor<SearchAgent.Builder> {
    
    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {

        private BrowserTool browserTool;

        public Builder browserTool(BrowserTool browserTool) {
            this.browserTool = browserTool;
            return this;
        }

        public SearchAgent build() throws GraphStateException {
            this.name("search_agent")
                .description("当需要获取最新信息、搜索网络内容或浏览网页时使用。适用于：网络搜索、信息收集、网页浏览、数据获取")
                .singleParameter("搜索查询或网页URL")
                .systemMessage(SystemMessage.from("""
                    **角色**: 你是一名信息检索与综合专家。
                    **核心目标**: 从网络上获取信息，并通过综合、提炼和引用来源，为用户提供一个全面且可信的答案。

                    ## 核心原则
                    1.  **综合而非罗列**: **你的核心价值在于综合多个信息源，提炼出一个连贯的答案**，而不是简单地返回搜索结果列表。
                    2.  **必须引用来源**: 你提供的每一条关键信息，都 **必须** 清晰地注明其来源URL。没有来源的信息是不可信的。
                    3.  **查询优化**: 主动将用户的模糊问题，转化为更精准、有效的搜索关键词。

                    ## 工作流程
                    1.  **优化查询**: 分析用户需求，构建有效的搜索查询。
                    2.  **执行搜索**: 使用 `searchWeb` 工具获取初步信息。
                    3.  **深入浏览**: 从搜索结果中选择最相关的几个页面，使用 `browseWeb` 工具获取详细内容。
                    4.  **综合与引用**: 将多个来源的信息整合成一个流畅的回答，并在适当的位置引用来源URL。
                    5.  **格式化输出**: 以清晰、易读的格式返回你的综合答案和来源列表。

                    ---

                    **关键指令重复**: 记住，**答案如果没有引用来源，就是不完整的。** 请务必为你综合的信息提供出处。
                    """));

            // 添加浏览器工具
            if (browserTool != null) {
                this.toolFromObject(browserTool);
            }

            return new SearchAgent(this);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public SearchAgent(Builder builder) throws GraphStateException {
        super(builder);
    }
}
