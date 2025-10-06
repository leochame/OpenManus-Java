package com.openmanus.agent.impl.executor;

import com.openmanus.agent.base.AbstractAgentExecutor;
import com.openmanus.agent.tool.FileTool;
import dev.langchain4j.data.message.SystemMessage;
import org.bsc.langgraph4j.GraphStateException;

/**
 * 文件智能体 - 负责文件操作任务
 * 
 * 核心功能：
 * 1. 文件读取和写入
 * 2. 目录操作和管理
 * 3. 文件内容分析和处理
 */
public class FileAgent extends AbstractAgentExecutor<FileAgent.Builder> {
    
    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {
        
        private FileTool fileTool;
        
        public Builder fileTool(FileTool fileTool) {
            this.fileTool = fileTool;
            return this;
        }
        
        public FileAgent build() throws GraphStateException {
            this.name("file_agent")
                .description("当需要读取、写入、创建或管理文件时使用。适用于：文件读写、目录操作、文件管理、内容处理")
                .singleParameter("文件操作需求和文件路径")
                .systemMessage(SystemMessage.from("""
                    **角色**: 你是一名文件系统操作专家。
                    **核心目标**: **建设性地**、安全地与文件系统进行交互，主要负责信息的读取、写入和发现。

                    ## 核心原则
                    1.  **安全优先**: **你的操作必须是非破坏性的。** 你只能执行读取、写入和列出目录内容的操作。
                    2.  **先查后用 (Check-Before-Use)**: 在尝试读取文件之前，你最好先通过 `listDirectory` 确认文件是否存在，以提高操作的成功率。
                    3.  **意图清晰**: 明确你要执行的操作是 `readFile`, `writeFile`, 还是 `listDirectory`。

                    ## 工作流程
                    1.  **分析需求**: 理解用户的文件操作请求，明确目标路径和操作类型。
                    2.  **（可选但推荐）预检路径**: 如果不确定路径是否存在，先使用 `listDirectory` 进行检查。
                    3.  **调用工具**: 根据分析，选择 `readFile`, `writeFile`, 或 `listDirectory` 工具并执行。
                    4.  **报告结果**: 清晰地返回操作结果，例如文件内容、写入成功确认或目录列表。

                    ---

                    **关键指令重复**: 记住，**你的职责是建设性的，严禁任何形式的删除操作。**
                    """));
            
            // 添加文件工具
            if (fileTool != null) {
                this.toolFromObject(fileTool);
            }
            
            return new FileAgent(this);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public FileAgent(Builder builder) throws GraphStateException {
        super(builder);
    }
}
