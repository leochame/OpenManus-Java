# Multi-Agent MVP 开发计划

本文档用于收敛 issue `#2`「support Agent Team collaboration」的第一版实现范围。

当前约束以仓库总纲和最新沟通为准：

- 遵守 `AGENTS.md` 中“小步推进、逐步完成、优先最小可运行链路”的原则。
- 第一版**不引入角色定义**；子 Agent 与主 Agent 使用**相同能力、相同工具、相同默认执行方式**。
- 第一版目标是补齐最小的 Multi-Agent 协作闭环，而不是一次性实现技能系统、事件系统、定时调度、环境监控等完整能力。

## 1. 目标

在当前单 Agent 主链路稳定的前提下，为仓库增加最小 Multi-Agent 协同能力，支持：

1. 主 Agent 创建子 Agent。
2. 主 Agent 向子 Agent 分配任务。
3. 子 Agent 基于当前已有执行能力独立完成任务。
4. 子 Agent 将结果返回给主 Agent。
5. 主 Agent 汇总子 Agent 结果并继续推进当前任务。

第一版不要求：

- 不要求子 Agent 拥有独立角色。
- 不要求子 Agent 拥有差异化工具集。
- 不要求完整共享全部上下文历史。
- 不要求事件总线、cron、环境监控、自动恢复。
- 不要求动态技能加载与版本兼容。

## 2. 范围边界

### 2.1 本次要做

- 定义最小 Team 协作模型。
- 定义主 Agent 与子 Agent 的任务委派协议。
- 在现有单 Agent 执行链路之上增加 Team 协调层。
- 复用现有 `AgentCoordinator` / `AbstractAgentExecutor` / 工具注册机制构建子 Agent。
- 为多 Agent 任务流增加最小测试覆盖。

### 2.2 本次不做

- `AgentRole`、角色权限控制、差异化能力配置。
- 中央技能注册中心与运行时热加载。
- 通用事件总线、触发器引擎、环境监控器。
- 跨进程 / 跨节点调度。
- 分布式共享状态。
- 为未来扩展预埋复杂抽象框架。

## 3. 设计原则

1. **单 Agent 主链路不回退**
   Multi-Agent 作为新增能力接入，不破坏当前单 Agent 默认执行链路。

2. **协调层与执行层分离**
   `AgentCoordinator` 继续负责单 Agent ReAct/CodeAct 执行闭环；Multi-Agent 调度逻辑不直接塞进单 Agent 执行器内部。

3. **先任务委派，再考虑复杂通信**
   第一版优先打通“创建子 Agent -> 分配任务 -> 返回结果 -> 主 Agent 汇总”的闭环。

4. **先传递任务上下文切片，不做全量上下文共享**
   子 Agent 接收的是主 Agent 明确下发的任务描述与必要上下文，而不是直接共享完整会话记忆。

5. **复用现有能力，不平行重造**
   子 Agent 默认复用现有模型、工具注册与执行器配置，避免新造一套运行时。

## 4. 建议的最小模型

第一版建议只定义以下核心模型，保持精简：

- `TeamTask`
  - 表示一个被委派给子 Agent 的任务。
  - 关键字段建议包括：`taskId`、`parentSessionId`、`delegatorAgentName`、`assigneeAgentName`、`taskPrompt`、`status`、`resultSummary`、`createdAt`。

- `TeamTaskStatus`
  - 建议使用简单枚举：`PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`。

- `SubAgentExecutionRequest`
  - 表示主 Agent 发起的一次子任务执行请求。
  - 关键字段建议包括：`taskPrompt`、`contextSummary`、`conversationId/sessionId`、`parentTaskId`。

- `SubAgentExecutionResult`
  - 表示子 Agent 的执行结果。
  - 关键字段建议包括：`status`、`finalAnswer`、`errorMessage`、`toolTraceSummary`。

第一版暂不引入：

- `AgentRole`
- `PermissionPolicy`
- `SkillDefinition`
- `EventBusMessage`

这些能力如果后续需要，可以在 Multi-Agent MVP 跑通后再增量补齐。

## 5. 模块拆分建议

### 5.1 Domain 层

职责：

- 定义 Team 任务与执行结果的领域模型。
- 定义最小调度 port，不承载 Spring 或运行时细节。

建议新增内容：

- `TeamTask`
- `TeamTaskStatus`
- `SubAgentExecutionRequest`
- `SubAgentExecutionResult`
- `TeamExecutionPort` 或 `SubAgentExecutionPort`

### 5.2 Agent 层

职责：

- 持有 Multi-Agent 协调逻辑。
- 负责主 Agent 如何创建与调用子 Agent。
- 控制上下文切片、委派顺序与结果汇总。

建议新增内容：

- `TeamCoordinator` 或 `MultiAgentCoordinator`
  - 负责任务拆分、子 Agent 调用、结果汇总。
- `SubAgentFactory`
  - 负责基于现有 `AgentCoordinator` 配置构造子 Agent 实例。
- `SubAgentInvocationService`
  - 封装一次子 Agent 执行流程，隔离调度层与底层执行器。

### 5.3 Infra 层

职责：

- 提供默认内存态实现与 Spring 装配。
- 对外暴露运行时装配入口。

建议新增内容：

- `DefaultSubAgentExecutionService`
- `InMemoryTeamTaskStore` 或最小内存记录器
- 对应 `@Configuration` 装配

### 5.4 aiframework 层

第一版原则：

- 尽量不改，或只做非常小的复用型改动。
- 如果 Team 协作不需要通用框架抽象，就不要把逻辑下沉到 `aiframework`。

## 6. 推荐实施阶段

## 阶段 A：设计收敛

目标：

- 明确 Multi-Agent MVP 范围。
- 确认第一版不做角色与权限分化。
- 确认子 Agent 复用主 Agent 相同工具集与能力配置。

产出：

- 本文档。
- 必要时补充到 `docs/TECHNICAL_IMPLEMENTATION.md` 与 `docs/DEVELOPMENT_PROGRESS.md`。

完成标准：

- 团队对第一版边界达成一致。
- 不再把 cron / event / monitor / skills 一次性并入第一版实现。

## 阶段 B：最小模型与接口

目标：

- 新增 Team 协作最小模型与接口。
- 不动主链路行为，只补充结构。

建议工作项：

1. 定义 `TeamTask`、`TeamTaskStatus`。
2. 定义 `SubAgentExecutionRequest`、`SubAgentExecutionResult`。
3. 定义 `SubAgentExecutionPort`。

完成标准：

- 结构清晰。
- 分层不回退。
- 不出现为了未来扩展而过度抽象的通用 orchestrator 框架。

## 阶段 C：子 Agent 构造与执行复用

目标：

- 能创建与主 Agent 同能力的子 Agent。
- 子 Agent 可独立执行一个任务 prompt 并返回结果。

建议工作项：

1. 提供 `SubAgentFactory`，复用现有模型、memory provider、工具注册配置。
2. 明确子 Agent 的 system prompt 策略。
   - 第一版可以与主 Agent 基本一致。
   - 只补充“你是被主 Agent 委派的执行单元”这类最小约束。
3. 封装单次子 Agent 执行入口。

完成标准：

- 给定一个任务 prompt，可以成功执行子 Agent 并返回文本结果。
- 不要求并发，不要求复杂状态同步。

## 阶段 D：主 Agent 委派闭环

目标：

- 打通主 Agent 调用子 Agent 的最小闭环。

建议工作项：

1. 设计委派入口。
   - 可以是新增内部服务调用。
   - 也可以是新增一个给主 Agent 使用的 team/delegate 工具。
2. 主 Agent 创建 `TeamTask`。
3. 调用子 Agent 完成任务。
4. 将子 Agent 结果结构化回传给主 Agent。
5. 主 Agent 基于返回结果继续回答。

完成标准：

- 至少支持单个子 Agent 委派。
- 最好补到支持顺序委派多个子 Agent，但不强求第一刀就做并发。

## 阶段 E：最小可观测性与测试

目标：

- 为 Multi-Agent MVP 补最小验证闭环。

建议工作项：

1. 增加单元测试：
   - 模型与状态流转测试。
   - `SubAgentFactory` / `SubAgentExecutionService` 测试。
2. 增加 smoke test：
   - 主 Agent 委派子 Agent 后成功汇总结果。
3. 如现有事件流足够复用，可补最小执行事件：
   - 子任务开始
   - 子任务结束
   - 子任务失败

完成标准：

- `compile` 通过。
- 默认测试通过。
- Multi-Agent 最小协作闭环有稳定自动化验证。

## 7. 推荐的开发顺序

建议按下面顺序推进：

1. 写清楚文档与边界。
2. 新增最小领域模型与 port。
3. 实现子 Agent 工厂与单次执行服务。
4. 实现主 Agent 委派闭环。
5. 补测试与最小事件。
6. 再评估是否进入下一阶段增强。

不建议的顺序：

- 先做角色系统。
- 先做技能中心。
- 先做事件总线。
- 先做 cron / trigger / 环境监控。

这些都容易把第一版范围拉爆。

## 8. 第一版建议验收口径

功能验收建议：

1. 主 Agent 可以发起一次子任务委派。
2. 子 Agent 可以复用现有工具完成任务。
3. 子 Agent 能返回结构化执行结果。
4. 主 Agent 能读取并整合结果。
5. 失败路径可控，至少能返回失败状态与错误摘要。

测试验收建议：

1. `./scripts/mvnw-local.sh -q -DskipTests compile`
2. `./scripts/mvnw-local.sh -q -DskipITs test`
3. 新增 Multi-Agent 相关 smoke test 通过

架构验收建议：

- `domain -> port -> infra adapter` 分层不回退。
- `agent` 层不混入 Spring 装配语义。
- Multi-Agent 逻辑不污染单 Agent 默认运行面。

## 9. 第二版以后再评估的增强项

当 Multi-Agent MVP 稳定后，再考虑以下增强：

1. 角色系统
   - 不同 Agent 拥有不同 system prompt、不同工具集、不同职责。

2. 技能系统
   - 统一 skills registry
   - skill discovery
   - 动态加载

3. 协作通信增强
   - 多子 Agent 并行
   - 更细粒度的任务状态同步
   - 更明确的消息协议

4. 事件与触发系统
   - 内部事件总线
   - 条件触发
   - 定时任务

5. 主动能力
   - cron
   - 环境监控
   - 自动恢复工作流

## 10. 当前结论

对于 issue `#2`，第一版最合理的实现目标不是“完整 Agent Team 平台”，而是：

**在不引入角色差异和复杂系统能力的前提下，先完成主 Agent 到子 Agent 的最小任务委派与结果汇总闭环。**

这是当前最符合仓库阶段目标、代码边界和开发节奏的实现路径。
