# Agent Team 上下文隔离开发方案

本文档用于规划 `agentteam` 在完成提示词隔离、工具隔离之后的下一阶段工作：上下文隔离。

当前目标不是一次性复刻 Claude Code 的完整 Subagent 机制，而是在现有 `agentteam` 最小协作链路上，逐步补齐父 Agent、Team Master、SubAgent 之间的运行时边界。

## 1. 背景

当前 `agentteam` 已经具备以下基础能力：

1. Team Master 可以判断任务是否适合拆分。
2. Team Master 可以生成多个 `SubTask`。
3. 多个 `SubAgentWorker` 可以从任务池中领取并执行子任务。
4. SubAgent 已经具备独立的系统提示词。
5. Team Master 和 SubAgent 已经具备不同的工具策略。
6. 主流程可以聚合子任务成功和失败结果。

但当前上下文边界仍然不够显式：

1. SubAgent 的聊天记忆隔离主要依赖 `taskId`，缺少统一的 memoryId 生成规则。
2. SubAgent 执行时缺少显式的 `parentSessionId/groupId/taskId/agentId/depth` 运行身份。
3. 子任务描述中尚未稳定承载 Team Master 下发的必要背景上下文。
4. 任务状态写回缺少 agentId 级别的所有权校验。
5. 消息总线已有雏形，但 mailbox 消息尚未真正注入 SubAgent 执行上下文。

因此下一阶段应先完成上下文隔离的最小闭环，再逐步增强消息驱动通信和恢复能力。

## 2. 目标

本阶段目标是让每个 SubAgent 成为一个具有清晰运行边界的执行单元：

1. SubAgent 不读取父 Agent 的完整聊天历史。
2. SubAgent 使用独立的聊天 memoryId。
3. SubAgent 只接收 Team Master 下发的当前子任务和必要上下文切片。
4. SubAgent 只能更新自己领取的任务状态。
5. Team Master 保留任务组视图和结果聚合职责。
6. 普通单 Agent 主链路不受影响。

完成后应满足：

- 父 Agent 的 memory 与 SubAgent memory 分离。
- SubAgent 的任务上下文由后端明确注入，而不是由 SubAgent 自行读取父上下文。
- 任务状态读写有明确归属，避免多个 worker 互相污染。
- 后续可以平滑演进到消息驱动通信、失败重试、恢复执行。

## 3. 非目标

本阶段不做以下内容：

1. 不实现 Fork Subagent 或 prompt cache 优化。
2. 不实现 completed SubAgent 自动唤醒。
3. 不实现完整事件总线。
4. 不实现跨进程或分布式 Agent Team。
5. 不引入复杂 RBAC 权限系统。
6. 不让 SubAgent 直接读取父 Agent 完整聊天历史。
7. 不让 SubAgent 再次创建新的 SubAgent。
8. 不把上下文隔离逻辑下沉为 `aiframework` 通用框架能力。

## 4. 上下文分类与隔离策略

### 4.1 聊天上下文

含义：

- system message
- user message
- assistant message
- tool call message
- tool result message
- 当前 memoryId 对应的历史消息

隔离策略：

- 父 Agent 使用父会话 memoryId。
- SubAgent 使用独立 memoryId。
- SubAgent 不直接读取父 Agent memory。
- 如需父任务背景，由 Team Master 生成 summary 后写入 `SubTask`。

建议 memoryId 格式：

```text
agentteam:{parentSessionId}:{groupId}:{taskId}:{agentId}
```

第一版可以先不做复杂转义，仅保证格式稳定。后续如 sessionId 中存在特殊字符，再补充 sanitize。

### 4.2 任务上下文

含义：

- `TaskGroup`
- `SubTask`
- `TaskStatus`
- `claimedBy`
- 子任务标题、描述、必要背景
- 子任务结果、失败原因

隔离策略：

- Team Master 拥有 TaskGroup 全局视图。
- SubAgent 只接收当前 `SubTask` 的任务切片。
- `SubTask` 中应包含 Team Master 下发的 `contextSummary`。
- Worker 从 `TaskPoolPort` claim 子任务。
- 任务成功/失败写回时必须校验 `agentId` 与任务领取者一致。

### 4.3 工具上下文

含义：

- 当前角色可用工具列表
- 工具 schema
- 工具执行结果

隔离策略：

- 继续沿用 `TeamMasterToolPolicy` 与 `SubAgentToolPolicy`。
- SubAgent 不拥有调度类工具。
- SubAgent 工具调用历史保留在自己的 memory 中。
- Team Master 只消费子任务汇总结果，不消费 SubAgent 完整工具历史。

### 4.4 执行状态上下文

含义：

- ReAct/CodeAct 循环中的 plan
- inProgress
- todo
- lastFailure
- pending tool results
- 重复工具调用检测状态

隔离策略：

- 第一版依赖每次 `AgentCoordinator.execute` 的局部状态自然隔离。
- 通过独立 SubAgent memoryId，避免执行状态卡片进入父 Agent 历史。
- 后续如要 resume，再把执行状态纳入 `AgentTeamExecutionContext` 或独立状态仓储。

### 4.5 环境上下文

含义：

- workspace
- sandbox
- session sandbox
- 当前项目目录
- shell/browser/python 执行环境

隔离策略：

- 第一版允许 SubAgent 共享父 session 的 workspace/sandbox。
- 共享环境不等于共享聊天 memory。
- `parentSessionId` 用于环境归属。
- `memoryId` 用于聊天历史归属。

### 4.6 身份上下文

含义：

- role
- parentSessionId
- groupId
- taskId
- agentId
- depth
- memoryId

隔离策略：

- 新增显式 `AgentTeamExecutionContext`。
- 每次 Team Master 或 SubAgent 执行都构造上下文对象。
- 第一版 `depth` 对 SubAgent 固定为 `1`。
- 后续用于防递归、日志追踪、事件 metadata、消息路由。

### 4.7 消息上下文

含义：

- `AgentMessage`
- mailbox unread/read 状态
- fromAgentId/toAgentId
- message type
- content

隔离策略：

- 当前阶段保留 message bus 雏形。
- 第一版上下文隔离不强制实现动态消息注入。
- 后续增强时，Worker 拉取 mailbox 后应把消息渲染为 SubAgent prompt 的一部分，而不是只标记已读。

### 4.8 结果上下文

含义：

- summary
- detail
- evidence
- errorMessage
- status

隔离策略：

- SubAgent 输出结构化结果。
- Team Master 只读取 `SubTaskExecutionOutput` / `TaskGroupResult`。
- Team Master 不直接读取 SubAgent 完整 memory 或完整工具调用轨迹。

## 5. 分阶段开发计划

## 阶段 A：聊天上下文 memoryId 隔离（已完成）

目标：

- SubAgent 使用明确的独立 memoryId。
- 不再直接使用裸 `taskId` 作为 SubAgent conversationId。

建议改动：

1. 新增 `AgentTeamMemoryIds`。
2. 提供 SubAgent memoryId 生成方法。
3. 修改 `SubAgentExecutionService`，调用 role runtime 时使用生成后的 memoryId。

建议类：

```text
src/main/java/com/openmanus/agentteam/application/AgentTeamMemoryIds.java
```

建议 API：

```java
public final class AgentTeamMemoryIds {

    public static String subAgent(String parentSessionId, String groupId, String taskId, String agentId) {
        return String.join(":",
                "agentteam",
                safe(parentSessionId),
                safe(groupId),
                safe(taskId),
                safe(agentId));
    }
}
```

验收标准：

1. SubAgent memoryId 以 `agentteam:` 开头。
2. SubAgent memoryId 包含 `groupId/taskId/agentId`。
3. SubAgent memoryId 不等于父 `conversationId`。
4. 现有单 Agent 回归测试不受影响。

建议测试：

- `SubAgentExecutionIsolationTest`
- 新增 `AgentTeamMemoryIdsTest`

## 阶段 B：任务上下文切片（已完成）

目标：

- SubTask 显式携带 Team Master 下发的必要背景。
- SubAgent prompt 只注入当前子任务切片。

建议改动：

1. 为 `SubTaskPlan` 增加 `contextSummary`。
2. 为 `SubTask` 增加 `contextSummary`。
3. 调整 `TaskDecompositionService` 的解析与兜底逻辑。
4. 调整 `subagent-execution.prompt.md`，注入 `contextSummary`。
5. 调整 `SubAgentExecutionService.buildSubTaskPrompt`。

`contextSummary` 第一版来源：

- 优先使用模型拆解 JSON 中每个子任务的 `contextSummary`。
- 如果缺失，则使用父任务输入生成简单兜底背景。

兜底示例：

```text
Parent request:
{{userInput}}

Team instruction:
Complete only this assigned subtask. Return concise evidence and result for Team Master aggregation.
```

验收标准：

1. SubAgent prompt 包含当前任务的 `title/description/contextSummary`。
2. SubAgent prompt 不包含其他 SubTask 的 description。
3. 当 LLM 拆解结果缺少 `contextSummary` 时，系统能生成兜底上下文。

建议测试：

- `TaskDecompositionServiceTest`
- `SubAgentExecutionIsolationTest`
- `MasterAgentOrchestratorTest`

## 阶段 C：显式执行上下文（已完成）

目标：

- 将 role、parentSessionId、groupId、taskId、agentId、depth、memoryId 收束到统一对象。
- 为后续日志、事件、消息、恢复提供稳定入口。

建议新增：

```text
src/main/java/com/openmanus/agentteam/application/AgentTeamExecutionContext.java
```

建议结构：

```java
public record AgentTeamExecutionContext(
        AgentTeamRole role,
        String parentSessionId,
        String groupId,
        String taskId,
        String agentId,
        int depth,
        String memoryId
) {

    public static AgentTeamExecutionContext subAgent(
            String parentSessionId,
            String groupId,
            String taskId,
            String agentId
    ) {
        return new AgentTeamExecutionContext(
                AgentTeamRole.SUB_AGENT,
                parentSessionId,
                groupId,
                taskId,
                agentId,
                1,
                AgentTeamMemoryIds.subAgent(parentSessionId, groupId, taskId, agentId)
        );
    }
}
```

建议改动：

1. 调整 `AgentTeamRoleExecutionPort`，新增基于 context 的执行方法。
2. 调整 `AgentTeamRoleExecutionService`，从 context 读取 role 和 memoryId。
3. 调整 `SubAgentExecutionService`，使用 context 构造 prompt 和执行请求。
4. 在 MDC 或日志中补充 context 字段。

验收标准：

1. SubAgent 执行入口不再只传 `AgentTeamRole + conversationId`。
2. 测试中可以断言 SubAgent context 的 role、memoryId、agentId。
3. 日志中能追踪 parentSessionId、groupId、taskId、agentId。

建议测试：

- 新增 `AgentTeamExecutionContextTest`
- 调整 `SubAgentExecutionIsolationTest`
- 调整 `AgentTeamRoleExecutionServiceTest` 或补充现有测试

## 阶段 D：任务状态写入所有权校验（已完成）

目标：

- SubAgent 只能写回自己 claim 的任务。
- 避免多个 worker 或异常路径污染其他任务状态。

建议改动：

1. 调整 `TaskPoolPort`：

```java
void markSucceeded(String taskId, String agentId, String summary, String detail);

void markFailed(String taskId, String agentId, String errorMessage);
```

2. 调整 `InMemoryTaskPool` 实现。
3. 在写回前校验任务当前领取者。
4. 调整 `SubAgentWorker.executeClaimedTask` 调用。

建议规则：

```text
PENDING 任务不能直接 markSucceeded/markFailed。
未被当前 agentId claim 的任务不能被当前 agentId 写回。
已完成任务不能再次写回，除非后续明确引入 retry/reopen。
```

验收标准：

1. 正常领取任务的 worker 可以写回成功/失败。
2. 非领取者写回任务会失败。
3. 未领取任务不能直接写回成功。
4. 现有并行任务测试仍通过。

建议测试：

- `InMemoryTaskPoolTest`
- `MasterAgentOrchestratorTest`

## 阶段 E：结果上下文结构化

目标：

- SubAgent 输出更适合 Team Master 汇总。
- 减少父流程读取 SubAgent 完整执行历史的需求。

建议改动：

1. 扩展 `SubTaskExecutionOutput`。
2. 在 SubAgent prompt 中约束输出格式。
3. 在 `SubAgentExecutionService` 中做最小解析或保守降级。
4. `DefaultResultAggregationService` 优先消费结构化字段。

可选结构：

```java
public record SubTaskExecutionOutput(
        String summary,
        String detail,
        List<String> evidence,
        String errorMessage
) {
}
```

第一版可保留兼容：

- 如果无法解析结构化输出，则继续使用原始文本作为 `detail`。
- `summary` 仍使用当前摘要逻辑兜底。

验收标准：

1. SubAgent 结果包含 summary。
2. 可选 evidence 能被聚合服务展示。
3. 解析失败不影响主链路完成。

## 阶段 F：消息上下文注入

目标：

- 让现有 `AgentMessageBusPort` 从“只存消息”演进为“可影响 SubAgent 执行”的消息机制。

建议改动：

1. 调整 `SubAgentWorker.drainMailbox`，返回 unread messages。
2. 将 unread messages 传给 `SubAgentExecutionService`。
3. 在 `buildSubTaskPrompt` 中注入 mailbox context。
4. 只在成功注入后标记消息已读，避免消息丢失。

建议 prompt 片段：

```text
[Messages From Team Master]
- {{message}}
[/Messages From Team Master]
```

验收标准：

1. 发给某个 agentId 的消息只被该 agent 消费。
2. unread 消息能出现在 SubAgent prompt。
3. 消息注入后被标记为已读。
4. 无消息时不影响当前执行链路。

建议测试：

- `InMemoryAgentMessageBusTest`
- 新增 `SubAgentMailboxContextTest`

## 阶段 G：可观测性与文档收口

目标：

- 让上下文隔离行为可测试、可排查、可说明。

建议改动：

1. 日志中输出 context 摘要：
   - role
   - parentSessionId
   - groupId
   - taskId
   - agentId
   - memoryId
2. execution event metadata 中补充 context 字段。
3. 更新 `docs/DEVELOPMENT_PROGRESS.md`。
4. 补充上下文隔离测试说明。

验收标准：

1. compile 通过。
2. 默认 test 通过。
3. 上下文隔离相关测试覆盖：
   - memoryId 隔离
   - task prompt 切片
   - task ownership 校验
   - mailbox 注入

## 6. 推荐实施顺序

建议按以下顺序推进：

1. 阶段 A：聊天 memoryId 隔离。
2. 阶段 B：任务上下文切片。
3. 阶段 C：显式执行上下文。
4. 阶段 D：任务状态写入所有权校验。
5. 阶段 E：结果上下文结构化。
6. 阶段 F：消息上下文注入。
7. 阶段 G：可观测性与文档收口。

其中 A-D 是本轮上下文隔离 MVP 的核心。E-G 可以在 MVP 稳定后继续增强。

## 7. 第一版建议验收口径

功能验收：

1. Team Master 拆分出的每个 SubTask 都带有必要背景上下文。
2. SubAgent 执行时使用独立 memoryId。
3. SubAgent prompt 只包含当前子任务切片。
4. SubAgent 成功/失败只能写回自己领取的任务。
5. Team Master 可以聚合多个 SubAgent 的结果并返回最终输出。

测试验收：

1. `./scripts/mvnw-local.sh -q -DskipTests compile`
2. `./scripts/mvnw-local.sh -q -DskipITs test`
3. 新增上下文隔离相关测试通过。

架构验收：

1. 上下文隔离逻辑收口在 `agentteam` 模块。
2. 不污染普通单 Agent 默认主链路。
3. 不把 SubAgent 的完整聊天历史塞回父 Agent memory。
4. 不为了未来扩展提前引入复杂调度框架。

## 8. 后续增强方向

当上下文隔离 MVP 稳定后，再评估：

1. SubAgent resume。
2. completed SubAgent 唤醒。
3. XML 或结构化 task notification。
4. 更细粒度的上下文摘要器。
5. 长任务 auto-background。
6. prompt cache / Fork Subagent 优化。
7. 持久化 TaskPool / MessageBus。

## 9. 当前结论

提示词隔离和工具隔离完成之后，下一步不应继续堆叠更多 Team 能力，而应优先补齐上下文隔离。

本阶段最小闭环是：

```text
SubAgent 独立 memoryId
+ SubTask contextSummary
+ AgentTeamExecutionContext
+ task ownership 校验
```

这四项完成后，`agentteam` 才真正具备清晰的父子 Agent 运行边界。后续再做消息驱动通信、失败恢复、自动后台化，会有稳定落点。
