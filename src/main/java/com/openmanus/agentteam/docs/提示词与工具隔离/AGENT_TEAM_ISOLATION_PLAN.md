# Agent Team 提示词与工具隔离开发计划

## 1. 背景

当前仓库已经具备 `agentteam` 的最小协作闭环：

1. 主流程可以判断任务是否适合拆分。
2. 可以把任务拆成多个 `SubTask`。
3. 多个 `SubAgentWorker` 可以并行领取并执行子任务。
4. 主流程可以汇总成功/失败结果并输出最终文本。

但当前实现仍然存在一个明显问题：

- `agentteam` 中的 `master agent` 还没有和普通单 Agent 流程中的主 Agent 做清晰区分。
- `subagent` 当前仍然通过 `SubAgentExecutionService -> AgentExecutionPort` 直接复用默认单 Agent 执行链路。
- 因此，`普通主 Agent`、`Agent Team Master Agent`、`SubAgent` 三个角色在提示词和工具能力上尚未真正隔离。

这与 Claude Code 的多 Agent 设计原则不一致。Claude Code 的关键不是“能并行跑多个 agent”，而是：

1. 不同角色有不同职责。
2. 不同角色有不同提示词。
3. 不同角色有不同工具边界。
4. `subagent` 不能默认继承父 Agent 的全部权力。

本计划用于收敛下一阶段实现目标：先完成 `提示词隔离 + 工具隔离`，再继续推进上下文隔离和消息驱动通信增强。

## 2. 目标

本阶段目标不是扩展更多功能，而是把当前 `agentteam` 从“复用默认 Agent 的并行执行”演进为“具有明确角色边界的协作执行”。

本阶段完成后，应满足以下目标：

1. 明确区分三种角色：
   - 普通流程 Agent
   - Agent Team Master Agent
   - Agent Team SubAgent
2. 三种角色拥有不同的提示词职责说明。
3. Agent Team Master Agent 与 SubAgent 拥有不同的工具集合。
4. `subagent` 不再默认拥有主流程中的调度权和全局控制权。
5. 改造不破坏当前普通单 Agent 默认主链路。

## 3. 非目标

本阶段明确不做以下内容：

1. 不实现完整角色系统或通用 RBAC 权限系统。
2. 不实现 Skills Registry / Skill Loader。
3. 不实现 cron / trigger / environment monitor / auto recovery。
4. 不实现分布式调度或跨进程 Agent Team。
5. 不在本阶段引入复杂的上下文共享策略。
6. 不把 `agentteam` 权限逻辑下沉成 `aiframework` 的通用抽象。

## 4. 角色定义

### 4.1 普通流程 Agent

普通流程 Agent 指当前默认执行链路中的主 Agent。

职责：

1. 直接理解用户输入。
2. 直接调用工具完成任务。
3. 直接给出最终回答。

特点：

1. 是单兵执行者。
2. 工具集以“直接干活”为中心。
3. 不承担 Team 协调职责。

### 4.2 Agent Team Master Agent

Agent Team Master Agent 指多 Agent 模式中的协调者。

职责：

1. 判断任务是否适合拆分。
2. 生成子任务计划。
3. 创建和派发子任务。
4. 跟踪子任务状态。
5. 汇总子任务结果。
6. 形成最终答复。

特点：

1. 是协调者，不是默认全能执行者。
2. 工具集以“拆分、委派、查询、汇总”为中心。
3. 即使保留少量直接执行工具，也不应与普通流程 Agent 完全相同。

### 4.3 Agent Team SubAgent

SubAgent 指被 Team Master 派发出去的执行单元。

职责：

1. 接收单个明确子任务。
2. 使用受限工具完成子任务。
3. 返回结构化结果或失败信息。

特点：

1. 是受控 worker。
2. 只负责执行，不负责拆分、委派、汇总、最终答复。
3. 不应拥有调度权、全局控制权或默认全量工具。

## 5. 提示词隔离方案

提示词隔离的核心不是“多写几句说明”，而是让模型明确进入不同角色。

### 5.1 普通流程 Agent 提示词

定位：

- 单兵执行者

提示词目标：

1. 直接分析任务。
2. 直接使用工具。
3. 自行推进计划、执行、观察、调整。
4. 直接向用户回答。

当前状态：

- 继续沿用当前默认单 Agent 主链路提示词。

### 5.2 Agent Team Master Agent 提示词

定位：

- 协调者 / 调度者

提示词目标：

1. 优先判断任务是否适合拆分。
2. 优先生成并管理子任务，而不是亲自完成所有细节。
3. 明确自己负责：
   - 任务拆解
   - 子任务委派
   - 子任务状态跟踪
   - 结果汇总
4. 只有在不值得委派时，才少量直接执行。

建议提示词约束：

1. 明确写出“你的首要职责是协调多个 worker，而不是默认亲自完成所有子任务”。
2. 明确写出“只有适合并行且可独立执行的工作才拆分”。
3. 明确写出“最终答复由你统一汇总输出”。

### 5.3 SubAgent 提示词

定位：

- 受委派执行单元

提示词目标：

1. 明确自己正在执行一个被委派的子任务。
2. 明确只处理当前 `SubTask`，不要重新拆任务。
3. 明确不负责最终用户答复。
4. 明确输出重点是：
   - 子任务结果
   - 关键证据
   - 失败原因

建议提示词约束：

1. 明确写出“你不是最终协调者”。
2. 明确写出“不要再次委派子任务”。
3. 明确写出“仅在当前授权工具范围内完成工作”。
4. 明确写出“结果需要便于上游汇总”。

### 5.4 提示词文件拆分建议

当前已有：

- `prompts/agentteam/task-decomposition.prompt.md`
- `prompts/agentteam/subagent-execution.prompt.md`

建议新增或调整为：

1. `prompts/agentteam/team-master-system.prompt.md`
   - Team Master 角色总提示词
2. `prompts/agentteam/task-decomposition.prompt.md`
   - 拆分专用提示词，可保留或并入 Team Master 提示词体系
3. `prompts/agentteam/subagent-execution.prompt.md`
   - SubAgent 执行提示词

建议原则：

1. 不修改普通流程主 Agent 提示词语义。
2. `agentteam` 的 Master / SubAgent 提示词完全独立维护。
3. 提示词差异是角色差异，不是文案差异。

## 6. 工具隔离方案

工具隔离的目标是按角色分配工具，而不是把所有角色都接到同一个默认工具池。

### 6.1 当前工具来源

当前本地工具主要位于：

- `src/main/java/com/openmanus/agent/tool`

包括：

1. `BrowserTool`
2. `PythonExecutionTool`
3. `SearchTool`
4. `ShellTool`
5. `TaskReflectionTool`
6. `WebFetchTool`

并通过：

- `infra/config/AgentArchitectureConfig`

注册进入默认 `AgentCoordinator`。

此外还有：

1. 运行时发现的 MCP tools
2. `agentteam` 模块自身的调度能力

需要注意：

- `agent/tool` 中的是“执行工具”。
- `agentteam` 中的是“调度能力”。
- 二者不能混为一谈。

### 6.2 角色与工具能力划分

#### 普通流程 Agent 工具能力

普通流程 Agent 继续使用当前默认工具集：

1. Browser
2. Python
3. Search
4. WebFetch
5. Shell
6. TaskReflection
7. 可选 MCP tools

#### Agent Team Master Agent 工具能力

Team Master Agent 的主能力应从“执行工具”转向“协调工具”。

建议第一版 Team Master 核心能力包含：

1. 任务拆分
2. 创建任务组
3. 生成子任务
4. 派发子任务
5. 查询子任务状态
6. 汇总结果

Team Master 是否保留直接执行工具：

1. 第一版可保留少量只读或辅助工具，例如：
   - `SearchTool`
   - `WebFetchTool`
   - 受限 `ShellTool`
2. 不建议保留完整执行工具集并默认直接干活。

#### SubAgent 工具能力

SubAgent 只保留完成子任务所需的工作工具。

建议第一版允许：

1. `BrowserTool`
2. `PythonExecutionTool`
3. `SearchTool`
4. `WebFetchTool`
5. `ShellTool`
6. 受限 `TaskReflectionTool`

### 6.3 SubAgent 禁用能力

SubAgent 必须禁止以下能力：

1. 再次拆分任务
2. 再次派发子任务
3. 创建新的 TaskGroup
4. 修改其他 SubTask 的状态
5. 修改 TaskGroup 汇总状态
6. 控制 WorkerManager 生命周期
7. 接管最终用户答复
8. 默认开放所有 MCP tools
9. 无边界读取/写入全局任务历史

### 6.4 为什么第一步先做工具隔离

因为当前最明显的问题不是“不能并发”，而是：

1. `SubAgentExecutionService` 仍直接复用默认执行链路。
2. `subagent` 仍隐式拥有与主流程近似的能力集合。

先做工具隔离有以下好处：

1. 改动范围比上下文隔离更小。
2. 不会立即侵入整个执行上下文模型。
3. 可以先建立清晰角色边界。
4. 是后续上下文隔离和消息通信增强的前置基础。

## 7. 代码分层设计

本阶段改造要遵守现有边界：

1. `domain` 负责模型与规则边界。
2. `application` 负责编排、角色决策和执行入口。
3. `infra` 负责 Spring 装配、提示词资源加载和运行时适配。

### 7.1 application 层

application 层新增或调整的职责：

1. 定义 Agent Team 的角色类型
2. 定义角色到提示词的选择逻辑
3. 定义角色到工具策略的选择逻辑
4. 提供 Team Master 与 SubAgent 的独立执行入口

建议新增类：

1. `AgentTeamRole`
   - 角色枚举
   - 值建议：
     - `TEAM_MASTER`
     - `SUB_AGENT`

2. `AgentTeamPromptStrategy`
   - 根据角色返回对应提示词模板或提示词描述

3. `SubAgentToolPolicy`
   - 输入默认可用工具
   - 输出 SubAgent 可用工具

4. `TeamMasterToolPolicy`
   - 输入默认可用工具
   - 输出 Team Master 可用工具

5. `TeamMasterExecutionService`
   - Team Master 专用执行入口
   - 负责使用 Team Master 的提示词和工具策略

6. `SubAgentExecutionService`
   - 从“仅包装默认 AgentExecutionPort”调整为“走 SubAgent 专用执行入口”

建议原则：

1. 工具隔离策略收口在 `application`，不要散落到 controller。
2. 提示词选择逻辑收口在 `application`，不要散落到 Worker 或 Config。

### 7.2 domain 层

domain 层不直接依赖 Spring 和运行时实现，主要承载边界规则。

建议新增内容：

1. `AgentRoleCapabilities` 或等价模型
   - 描述某角色允许和禁止的能力类别
   - 不是为了做复杂权限系统，而是把规则显式化

2. `ToolAccessRule`
   - 可选
   - 若第一版只做名称级过滤，可先不引入

建议原则：

1. domain 层描述“边界是什么”。
2. application 层负责“边界怎么执行”。

### 7.3 infra 层

infra 层负责运行时装配，不负责业务决策。

建议新增或调整：

1. `ClasspathAgentTeamPromptProvider`
   - 扩展为支持 Team Master 与 SubAgent 不同提示词模板

2. `AgentTeamConfig`
   - 负责装配：
     - `AgentTeamPromptStrategy`
     - `SubAgentToolPolicy`
     - `TeamMasterToolPolicy`
     - Team Master / SubAgent 执行服务

3. 如需独立 Coordinator 构造器，可增加：
   - `AgentTeamCoordinatorFactory`
   - 负责基于裁剪后的工具集构造 Team Master / SubAgent 执行器

建议原则：

1. `infra` 负责“怎么装”。
2. `application` 负责“装什么策略”。

## 8. 推荐实现路径

### 阶段 A：角色收口

目标：

1. 明确普通 Agent、Team Master、SubAgent 的角色边界
2. 文档和代码命名先统一

工作项：

1. 引入 `AgentTeamRole`
2. 把 `MasterAgentOrchestrator` 的语义明确为 Team Master 调度者

### 阶段 B：提示词隔离

目标：

1. Team Master 与 SubAgent 使用不同提示词

工作项：

1. 扩展 `AgentTeamPromptProvider`
2. 增加 Team Master 专用提示词模板
3. 调整 `SubAgentExecutionService` 提示词约束

完成标准：

1. Team Master 提示词强调协调职责
2. SubAgent 提示词强调执行职责

### 阶段 C：工具隔离

目标：

1. Team Master 与 SubAgent 使用不同工具策略

工作项：

1. 实现 `SubAgentToolPolicy`
2. 实现 `TeamMasterToolPolicy`
3. 改造执行入口，不再让 SubAgent 无条件走默认全量工具集

完成标准：

1. SubAgent 无法再次委派子任务
2. SubAgent 无法触碰全局调度控制能力
3. 单 Agent 默认主链路不受影响

### 阶段 D：测试补齐

目标：

1. 为角色隔离提供自动化验证

建议测试：

1. `SubAgentToolPolicyTest`
2. `TeamMasterToolPolicyTest`
3. `SubAgentExecutionIsolationTest`
4. `AgentTeamRolePromptSelectionTest`
5. `SingleAgentRegressionTest`

## 9. 验收标准

本阶段验收建议如下：

### 功能验收

1. 普通单 Agent 默认链路行为不变
2. Team Master 与 SubAgent 提示词不同
3. Team Master 与 SubAgent 工具集合不同
4. SubAgent 不可递归派发新子任务
5. Team Master 保留汇总和最终答复职责

### 架构验收

1. `agentteam` 的角色隔离逻辑收口在 `agentteam` 模块
2. 不把复杂隔离逻辑散落到 controller
3. 不污染普通单 Agent 默认主链路
4. 不为未来扩展提前引入复杂权限框架

### 测试验收

1. `compile` 通过
2. 默认 `test` 通过
3. 新增角色隔离相关测试通过

## 10. 后续演进

当本阶段稳定后，再考虑继续推进：

1. 上下文隔离
   - Team Master / SubAgent 的独立执行上下文
2. 消息驱动通信增强
   - 父子 Agent 显式消息协议
3. 更细粒度的工具权限
   - 按工具名、按工具类别、按副作用等级控制
4. MCP tools 的角色化开放策略
5. 角色系统和 skills system 的后续版本设计

## 11. 当前结论

当前 `agentteam` 已具备最小协作闭环，但要向 Claude Code 学习，下一步不应继续堆叠更多功能，而应优先补齐角色边界。

本阶段最重要的收口是：

1. 把普通流程 Agent、Team Master、SubAgent 区分清楚。
2. 让 Team Master 与 SubAgent 的提示词不同。
3. 让 Team Master 与 SubAgent 的工具集合不同。

只有先把这三层角色隔离开，后面的上下文隔离、通信机制和更强的多 Agent 协作策略才有稳定落点。
