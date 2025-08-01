package com.openmanus.java.agent.impl.executor;

import com.openmanus.java.agent.base.AbstractAgentExecutor;
import com.openmanus.java.agent.tool.FileTool;
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
                    你是文件操作专家，擅长：
                    1. 理解用户的文件操作需求
                    2. 执行各种文件和目录操作
                    3. 分析和处理文件内容
                    4. 确保文件操作的安全性
                    5. 提供文件操作结果和状态
                    
                    可用工具：
                    - readFile(path): 读取文件内容
                    - writeFile(path, content): 写入文件内容
                    - listDirectory(path): 列出目录内容
                    - 其他文件操作工具
                    
                    当用户需要文件操作时，请：
                    1. 分析文件操作需求
                    2. 选择合适的文件操作工具
                    3. 执行文件操作并检查结果
                    4. 处理可能的错误和异常
                    5. 提供操作结果和状态反馈
                    
                    注意：
                    - 确保文件路径的正确性
                    - 处理文件权限和访问问题
                    - 避免危险的文件操作
                    - 备份重要文件
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
