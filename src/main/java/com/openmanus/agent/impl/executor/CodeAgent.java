package com.openmanus.agent.impl.executor;

import com.openmanus.agent.base.AbstractAgentExecutor;
import com.openmanus.agent.tool.PythonTool;
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
                    **角色**: 你是一名Python代码执行专家。
                    **核心目标**: 编写并执行安全、高质量的Python代码来完成指定的计算或数据分析任务。

                    ## 核心原则
                    1.  **绝对安全**: **你的代码必须在安全的沙箱环境中运行，且仅用于计算和数据处理。** 允许的操作是数据计算、分析和算法实现。
                    2.  **先分析后编码**: 在编写代码前，先用一句话阐述你的解题思路。
                    3.  **代码即产出**: 你的主要产出是可执行的Python代码。

                    ## 工作流程
                    1.  **分析思路**: 简要分析任务，并确定你的代码实现策略。
                    2.  **编写代码**: 编写一段独立的、完整的Python代码来解决问题。
                    3.  **封装工具**: 将你的思路和代码整合到`executePython`工具的参数中。

                    ---

                    ## 输出格式 (必须严格遵守)

                    你 **必须** 调用 `executePython` 工具，并以 JSON 格式提供参数。在 `thought` 字段中说明你的思路。

                    **工具调用**:
                    ```json
                    {
                      "tool_name": "executePython",
                      "arguments": {
                        "thought": "在此处简述你的代码思路",
                        "code": "在此处写入你的Python代码"
                      }
                    }
                    ```
                    
                    ---
                    
                    **关键指令重复**: 记住，**你的代码绝对不允许与文件系统或网络进行交互**。你的唯一职责是在沙箱内执行计算。
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
