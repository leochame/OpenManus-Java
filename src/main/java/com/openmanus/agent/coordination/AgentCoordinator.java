package com.openmanus.agent.coordination;

import com.openmanus.agent.base.AbstractAgentExecutor;
import com.openmanus.agent.tool.BrowserTool;
import com.openmanus.agent.tool.PythonExecutionTool;
import com.openmanus.agent.tool.ShellTool;
import com.openmanus.agent.tool.TaskReflectionTool;

/**
 * Agent 执行协调器：
 * - 统一 ReAct 循环
 * - 统一工具注册
 * - 统一系统提示词
 */
public class AgentCoordinator extends AbstractAgentExecutor<AgentCoordinator.Builder> {

    private static final String SYSTEM_PROMPT = """
            你是 OpenManus 的执行协调器，负责围绕当前用户问题组织检索、浏览、代码、文件和反思能力。

            规则：
            1. 只围绕当前用户问题行动，不补充无关话题，不做自我介绍，不展示能力清单。
            2. 涉及外部事实、数据、新闻、价格、营收、估值、收购等信息时，先取证再下结论；证据不足时明确说明不足。
            3. 能直接回答的问题直接回答；需要工具时再调用工具，并且每次调用都必须与当前任务直接相关。
            4. 工具调用后先读取结果再决定下一步，避免重复调用、跳步结论和无依据推断。
            5. 不得伪造历史执行结果、既往分析结论、具体数据或引用来源。
            6. 文件与命令操作必须限制在会话沙盒内，优先最小变更。
            7. 最终回答只输出本次任务得到的结论、依据和必要的下一步建议。
            """;

    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {

        private BrowserTool browserTool;
        private PythonExecutionTool pythonExecutionTool;
        private ShellTool shellTool;
        private TaskReflectionTool taskReflectionTool;

        public Builder browserTool(BrowserTool browserTool) {
            this.browserTool = browserTool;
            return this;
        }

        public Builder pythonExecutionTool(PythonExecutionTool pythonExecutionTool) {
            this.pythonExecutionTool = pythonExecutionTool;
            return this;
        }

        public Builder shellTool(ShellTool shellTool) {
            this.shellTool = shellTool;
            return this;
        }

        public Builder taskReflectionTool(TaskReflectionTool taskReflectionTool) {
            this.taskReflectionTool = taskReflectionTool;
            return this;
        }

        public AgentCoordinator build() {
            this.name("agent_coordinator")
                    .description("统一协调搜索、代码、文件与反思工具，完成端到端任务。")
                    .singleParameter("用户请求")
                    .systemMessage(this.configuredSystemMessage() == null || this.configuredSystemMessage().isBlank()
                            ? SYSTEM_PROMPT
                            : this.configuredSystemMessage());

            if (browserTool != null) {
                this.toolFromObject(browserTool);
            }
            if (pythonExecutionTool != null) {
                this.toolFromObject(pythonExecutionTool);
            }
            if (shellTool != null) {
                this.toolFromObject(shellTool);
            }
            if (taskReflectionTool != null) {
                this.toolFromObject(taskReflectionTool);
            }

            return new AgentCoordinator(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private AgentCoordinator(Builder builder) {
        super(builder);
    }
}
