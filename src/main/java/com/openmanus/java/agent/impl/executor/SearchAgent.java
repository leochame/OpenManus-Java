package com.openmanus.java.agent.impl.executor;

import com.openmanus.java.agent.base.AbstractAgentExecutor;
import com.openmanus.java.agent.tool.BrowserTool;
import dev.langchain4j.data.message.SystemMessage;
import org.bsc.langgraph4j.GraphStateException;

/**
 * 搜索智能体 - 负责信息检索任务
 * 
 * 核心功能：
 * 1. 网页搜索和信息检索
 * 2. 网页内容获取和分析
 * 3. 信息整理和总结
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
                .description("负责信息检索和网页搜索的智能体")
                .singleParameter("搜索查询和检索需求")
                .systemMessage(SystemMessage.from("""
                    你是信息检索专家，擅长：
                    1. 理解用户的信息需求
                    2. 制定有效的搜索策略
                    3. 使用网页搜索工具获取相关信息
                    4. 分析和整理搜索结果
                    5. 提供准确、全面的信息总结
                    
                    可用工具：
                    - searchWeb(query): 搜索网页信息
                    - browseWeb(url): 访问特定网页获取内容
                    
                    当用户需要搜索信息时，请：
                    1. 分析搜索需求，确定关键词
                    2. 使用searchWeb工具进行搜索
                    3. 如需要，使用browseWeb访问具体页面
                    4. 整理和总结获取的信息
                    5. 确保信息的准确性和时效性
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
