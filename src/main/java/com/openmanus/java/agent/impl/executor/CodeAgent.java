package com.openmanus.java.agent.impl.executor;

import com.openmanus.java.agent.base.AbstractAgentExecutor;
import com.openmanus.java.agent.tool.PythonTool;
import dev.langchain4j.data.message.SystemMessage;
import org.bsc.langgraph4j.GraphStateException;

/**
 * 代码智能体 - 负责代码执行任务
 * 
 * 核心功能：
 * 1. Python代码编写和执行
 * 2. 数据分析和计算
 * 3. 代码调试和优化
 */
public class CodeAgent extends AbstractAgentExecutor<CodeAgent.Builder> {
    
    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {
        
        private PythonTool pythonTool;
        
        public Builder pythonTool(PythonTool pythonTool) {
            this.pythonTool = pythonTool;
            return this;
        }
        
        public CodeAgent build() throws GraphStateException {
            this.name("code_agent")
                .description("当需要编写代码、执行计算、数据分析或处理编程任务时使用。适用于：Python代码执行、数据分析、算法实现、计算任务")
                .singleParameter("代码需求或计算任务描述")
                .systemMessage(SystemMessage.from("""
                    你是代码执行专家，擅长：
                    1. 理解用户的编程需求
                    2. 编写高质量的Python代码
                    3. 执行代码并处理结果
                    4. 进行数据分析和可视化
                    5. 调试和优化代码性能
                    
                    可用工具：
                    - executePython(code): 执行Python代码
                    
                    当用户需要代码执行时，请：
                    1. 分析任务需求，确定解决方案
                    2. 编写清晰、高效的Python代码
                    3. 使用executePython工具执行代码
                    4. 分析执行结果，确保正确性
                    5. 如有错误，进行调试和修正
                    6. 提供代码说明和结果解释
                    
                    注意：
                    - 确保代码安全性，避免危险操作
                    - 处理异常情况，提供错误信息
                    - 优化代码性能和可读性
                    """));
            
            // 添加Python工具
            if (pythonTool != null) {
                this.toolFromObject(pythonTool);
            }
            
            return new CodeAgent(this);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public CodeAgent(Builder builder) throws GraphStateException {
        super(builder);
    }
}
