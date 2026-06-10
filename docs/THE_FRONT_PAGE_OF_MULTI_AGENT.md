# Multi-Agent MVP 第一版开发计划

**核心链路：**

1. 前端把一个大任务发给主 agent。
2. 主 agent 先做一次“任务分析与拆分”。
3. 拆出来的子任务必须满足“可以独立执行、没有前后依赖、可以并行跑”。
4. 主 agent 把这些子任务放进任务池。
5. 多个子 agent 从任务池里领取任务。
6. 子 agent 异步执行，执行结果回写到任务池。
7. 主 agent 等所有子任务结束后，汇总结果，再给前端最终答复。

**任务池核心模型建议**：

先直接存内存，不做持久化？

TaskGroup
表示一次大任务拆分出来的一组并行子任务。

建议字段：

- groupId
- sessionId
- parentPrompt
- status
- totalCount
- completedCount
- failedCount
- createdAt

SubTask
表示一个具体子任务。

建议字段：

- taskId
- groupId
- title
- prompt
- contextSummary
- status
- assigneeAgentId
- createdAt
- claimedAt
- finishedAt
- resultSummary
- errorMessage
- retryCount

TaskStatus
建议枚举：

- PENDING
- CLAIMED
- RUNNING
- SUCCEEDED
- FAILED

对应 `待领取 -> 已领取 -> 执行中 -> 成功/失败` 五个状态

**子 agent**

第一版先不真的为每个子任务 new 一个线程直接跑，或让每个子任务都 new 一个独立 agent runtime 长驻等待。
目前方式是：

- 启动固定数量的 SubAgentWorker
- 每个 worker 在后台轮询任务池
- 抢到任务后，通过 SubAgentExecutionService 执行
- 执行完后回写结果
- 然后继续轮询下一个任务

**主agent**

轮询查看子任务状态是否都为SUCCEEDED 或 FAILED

**代码结构：**

domain

- TeamTaskGroup
- TeamSubTask
- TeamTaskStatus
- TaskDecompositionPlan
- SubAgentExecutionResult
- TaskPoolPort

agent

- MasterAgentOrchestrator
- TaskDecompositionService
- SubAgentFactory
- SubAgentExecutionService
- TaskResultAggregationService

infra

- InMemoryTaskPool
- SubAgentWorker
- SubAgentWorkerManager
- MultiAgentConfig
