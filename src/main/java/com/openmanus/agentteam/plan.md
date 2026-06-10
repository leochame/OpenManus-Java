# OpenManus-Java AgentTeam 开发计划

## 目标

第一版将 `agentteam` 实现为一个独立的纵向子模块，采用以下包结构：

- `com.openmanus.agentteam.application`
- `com.openmanus.agentteam.domain`
- `com.openmanus.agentteam.infra`

这一版先打通一个最小可运行的多 Agent 协作闭环：

1. 主 agent 接收一个大任务。
2. 主 agent 判断该任务是否适合拆成多个可并行子任务。
3. 子任务进入内存任务池。
4. sub-agent worker 通过短轮询领取任务。
5. agent 之间通过内存 mailbox / message bus 通信。
6. 主 agent 轮询任务组状态并汇总结果。

## 范围边界

### 第一版要做

- [x] 只支持 `fan-out / fan-in`
- [x] 只支持无依赖并行子任务
- [x] subagent 不做角色区分，能力和主 agent 保持一致
- [x] 支持内存版任务池
- [x] 支持内存版 agent 通信 mailbox
- [x] 支持主 agent 轮询任务组完成状态
- [x] 支持最小结果汇总

### 第一版明确不做

- [x] 不支持 DAG 依赖调度
- [x] 不支持角色系统
- [x] 不支持持久化存储和崩溃恢复
- [x] 不支持 git/worktree 隔离
- [x] 不支持同文件并行修改后的 merge 策略
- [x] 不支持自动重试策略

## 分层规划

### `com.openmanus.agentteam.domain`

这一层放稳定的领域模型、状态枚举、领域 port 和核心规则。

- [x] 创建任务与消息领域模型
  - `TaskGroup`
  - `SubTask`
  - `AgentMessage`
- [x] 创建状态枚举
  - `TaskStatus`
  - `TaskGroupStatus`
  - `MessageType`
- [x] 定义领域 port
  - `TaskPoolPort`
  - `AgentMessageBusPort`
  - `TaskGroupRepositoryPort`
- [x] 定义领域服务接口
  - `TaskGroupManager`
  - `ResultAggregationService`
- [x] 定义任务拆分结果模型
  - `DecompositionPlan`
  - `SubTaskPlan`

### `com.openmanus.agentteam.application`

这一层放用例编排逻辑，负责协调 domain 对象和现有 agent 执行能力。

- [x] 实现 `MasterAgentOrchestrator`
- [x] 实现 `TaskDecompositionService`
- [x] 实现 `SubAgentExecutionService`
- [x] 实现 `AgentTeamApplicationService` 或等价 facade
- [x] 在不破坏单 agent 流程的前提下接入现有执行链路
- [x] 增加回退逻辑：拆分不安全时退回单 agent 执行

### `com.openmanus.agentteam.infra`

这一层放具体运行时实现。

- [x] 实现 `InMemoryTaskPool`
- [x] 实现 `InMemoryTaskGroupRepository`
- [x] 实现 `InMemoryAgentMessageBus`
- [x] 实现 `SubAgentWorker`
- [x] 实现 `SubAgentWorkerManager`
- [x] 增加 worker 数量与轮询间隔配置
- [x] 完成 Spring Bean 装配

## 实施顺序

### Phase 1：骨架与领域层

- [x] 创建 `src/main/java/com/openmanus/agentteam/` 包骨架
- [x] 增加领域模型与状态枚举
- [x] 增加领域 port
- [x] 增加任务组状态计算逻辑

完成标准：

- 领域类型能单独编译和测试
- 暂时不接运行时 wiring

### Phase 2：Infra MVP

- [x] 增加内存版任务池
- [x] 增加内存版任务组仓库
- [x] 增加内存版 mailbox / message bus
- [x] 验证任务提交、领取、成功、失败流转

完成标准：

- 单进程内可以正确提交和领取任务
- 并发下不会重复 claim 同一个任务

### Phase 3：Worker 运行时

- [x] 增加 sub-agent worker 循环
- [x] 增加短轮询行为
  - 空闲时 sleep
  - 有任务时立即 claim
- [x] 在 worker 循环中加入 mailbox 轮询
- [x] 基于 `ThreadPoolExecutor` 增加 worker manager

完成标准：

- 多个 worker 可以并行运行
- worker 一轮循环里既能收消息，也能领任务

### Phase 4：应用编排层

- [x] 增加主 agent 编排流程
- [x] 增加任务拆分结果校验
- [x] 增加任务组轮询逻辑
- [x] 增加子任务结果汇总
- [x] 增加拆分不安全时回退单 agent 的逻辑

完成标准：

- 内存版多 agent 流程端到端跑通
- 主 agent 能知道一组子任务是否都完成了

### Phase 5：接入现有 Agent 层

- [x] 将 subtask 执行接到现有 agent 执行能力
- [x] 保证 subagent 与主 agent 的工具能力一致
- [x] 默认不破坏当前单 agent 路径
- [x] 增加开关控制是否启用 `agentteam`

完成标准：

- 现有单 agent 流程和 smoke 路径仍可通过
- 多 agent 路径可以通过开关单独启用

### Phase 6：测试与验证

- [x] 增加领域状态流转单测
- [x] 增加任务 claim 并发测试
- [x] 增加 mailbox send/read/mark-read 测试
- [x] 增加完整 task-group 流程 smoke test
- [x] 增加部分失败场景测试

完成标准：

- happy path 和 failure path 都有覆盖
- claim 关键并发路径没有明显竞态

## 详细编码清单

实现时按下面顺序推进：

- [x] Step 1: 创建 `agentteam` 包骨架
- [x] Step 2: 增加领域模型与状态枚举
- [x] Step 3: 增加领域 port 与服务接口
- [x] Step 4: 增加内存版 infra 实现
- [x] Step 5: 增加 worker manager 与轮询循环
- [x] Step 6: 增加 application orchestrator
- [x] Step 7: 接入现有 agent 执行链路
- [x] Step 8: 增加配置与 Bean 装配
- [x] Step 9: 增加测试
- [x] Step 10: 更新进度文档

## 实现约束

- [x] 优先复用现有结构，不要过度抽象
- [x] `agentteam` 逻辑不要散落到 controller
- [x] `aiframework` 不承载 team 协作语义
- [x] 领域规则和线程池 / Spring wiring 分离
- [x] 第一版优先使用 JDK 并发原语
- [x] mailbox 是通信模型，不是持久化方案

## 当前进度说明

当前已经完成：

- `agentteam` 包骨架
- 领域模型、状态枚举、port、基础结果模型
- `TaskGroupStatusCalculator`
- `DefaultTaskGroupManager`
- `DefaultResultAggregationService`
- `TaskDecompositionService`
- `SubAgentExecutionService`
- `InMemoryTaskPool`
- `InMemoryTaskGroupRepository`
- `InMemoryAgentMessageBus`
- `SubAgentWorker`
- `SubAgentWorkerManager`
- `MasterAgentOrchestrator`
- `AgentTeamApplicationService`
- `AgentTeamConversationApplicationService`
- `AgentTeamProperties`
- `AgentTeamConfig`
- `DomainServiceConfig` 中的接线
- `AgentController` 中的可选 `agentTeam=true` 入口

当前验证情况：

- 主代码 `compile` 已通过
- 以下测试已通过：
  - `TaskGroupStatusCalculatorTest`
  - `DefaultTaskGroupManagerTest`
  - `DefaultResultAggregationServiceTest`
  - `TaskDecompositionServiceTest`
  - `InMemoryTaskPoolTest`
  - `InMemoryAgentMessageBusTest`
  - `MasterAgentOrchestratorTest`
  - `WebSocketExecutionStreamPublisherTest`
  - `AgentControllerSessionSandboxStartTest`
- 已覆盖 happy path、claim 并发、mailbox 收发已读、端到端编排成功、端到端部分失败

## 勾选规则

后续如果继续增强：

- 完成一项就把 `- [ ]` 改成 `- [x]`
- 如果某项延期或放弃，要在本文档里明确标注
- 这份文件作为 `agentteam` MVP 的单一跟踪清单
