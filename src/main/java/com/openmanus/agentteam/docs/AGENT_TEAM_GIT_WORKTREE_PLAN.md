# AgentTeam 基于 Git Worktree 的多 Agent 并行编码开发方案

本文档用于收敛 `agentteam` 下一阶段能力建设：参考 Claude Code 的隔离式并行协作思路，为 OpenManus-Java 增加“多 Agent 并行写代码”能力。

当前方案遵守仓库总纲：

- 小步推进，逐步完成。
- 优先最小可运行链路，不一次性做完整平台。
- 不破坏当前默认单 Agent 主链路。
- Domain、Agent、Infra、aiframework 职责清晰。

---

## 1. 背景

当前仓库内的 `agentteam` 已具备第一版协作能力：

- 主 Agent 可拆分任务。
- 可创建子任务并由多个 worker 执行。
- 可汇总子任务结果并返回最终文本结果。

但当前实现仍然更接近“单进程内任务并发执行”，还不是“多 Agent 并行写代码”：

1. 子 Agent 没有真正的代码工作区隔离。
2. 子 Agent 没有独立的 Git 分支与提交产物。
3. 主 Agent 汇总的是文本结果，不是工程交付物。
4. 没有多分支集成、冲突检测、集成验证这一层能力。

如果目标是“参考 Claude Code 的做法”，那么第一原则应从“共享工作区并发执行”转为“独立 worktree 隔离执行”。

---

## 2. 目标

本阶段目标不是做完整多 Agent 平台，而是补齐最小多 Agent 并行编码闭环：

1. 主 Agent 接收一个编码任务。
2. 判断任务是否适合拆分为多个可独立执行的代码子任务。
3. 为每个子任务创建独立 Git worktree 和独立分支。
4. 子 Agent 只在自己的 worktree 中读取、修改、测试代码。
5. 子 Agent 结束后产出结构化结果：
   - 修改摘要
   - 变更文件列表
   - 测试结果
   - 分支名
   - commit sha
   - worktree 路径
6. 主 Agent 汇总多个子 Agent 结果。
7. 在可控前提下执行本地集成合并与统一验证。

---

## 3. 非目标

本阶段明确不做以下内容：

1. 不做 GitHub OAuth、账号绑定、仓库创建。
2. 不把“自动 push 到 GitHub / 自动建 PR”作为第一版必需能力。
3. 不做跨机器或分布式多 Agent 调度。
4. 不做 DAG 编排或多阶段依赖图调度。
5. 不做自动冲突修复。
6. 不做完整角色权限系统或技能中心。
7. 不做无 Git 环境下的目录复制兼容主方案。

说明：

- 第一版默认依赖本机已安装 Git。
- 没有 Git 时，应禁用该模式并回退普通单 Agent 模式。
- GitHub 仅作为后续增强项，不是当前阶段运行前提。

---

## 4. 核心设计原则

### 4.1 默认单 Agent 主链路不回退

`agentteam` 的 worktree 编码能力是新增能力，不替换当前默认执行路径。

### 4.2 共享仓库，隔离工作区

多个子 Agent 不应在同一工作目录下并发改代码，而应使用独立 worktree。

### 4.3 以 Git 交付物为中心

子 Agent 的结果不应只是一段文本，而应是：

- 分支
- commit
- diff 摘要
- 测试结论
- 集成状态

### 4.4 先支持低冲突任务

第一版只允许“文件范围基本不重叠”的任务并行执行。

### 4.5 先本地闭环，再远程协作

第一版先完成：

- 本地 worktree
- 本地 branch
- 本地 commit
- 本地 merge / cherry-pick

之后再考虑：

- push 到远程
- GitHub PR
- 代码评审自动化

---

## 5. 运行前提

### 5.1 必需前提

1. 当前工作目录必须是 Git 仓库。
2. 当前环境必须可执行 `git --version`。
3. 仓库工作区应处于可接受状态：
   - 至少主流程能识别当前基线分支或 HEAD
   - 可根据策略决定是否允许脏工作树进入多 Agent 模式

### 5.2 可选前提

1. 如用户本地已配置远程仓库，可在后续阶段支持 push。
2. 如用户本地已配置 GitHub 认证，可在后续阶段支持自动 push。

### 5.3 失败回退

以下场景直接不进入 worktree 模式：

1. 未安装 Git。
2. 当前目录不是 Git 仓库。
3. 当前仓库状态不满足安全执行条件。
4. 任务不适合拆分为独立代码子任务。

回退策略：

- 返回结构化失败原因。
- 自动回退到普通单 Agent 模式，或提示用户当前环境不支持该模式。

---

## 6. Worktree 模式说明

本方案中的 `worktree` 不是简单复制代码目录，也不是单纯创建分支，而是：

1. 在同一 Git 仓库下创建多个独立工作目录。
2. 每个工作目录对应独立分支。
3. 每个子 Agent 只在自己的工作目录中操作代码。
4. 最终通过本地 Git 集成这些结果。

示例目录：

- 主仓库：`E:\Project\OpenManus-Java`
- worktree A：`E:\Project\OpenManus-Java\.agentteam\worktrees\task-api`
- worktree B：`E:\Project\OpenManus-Java\.agentteam\worktrees\task-ui`

示例分支：

- `agentteam/task-api`
- `agentteam/task-ui`
- `agentteam/integration-<id>`

---

## 7. 总体架构建议

建议在现有 `agentteam` 模块中新增一条“并行编码编排链路”，而不是继续扩展当前内存 worker 逻辑。

推荐分层如下：

### 7.1 Domain 层

职责：

- 定义并行编码任务模型。
- 定义 worktree 会话模型。
- 定义子 Agent 结构化结果模型。
- 定义集成结果模型。

建议新增模型：

1. `CodeTaskGroup`
2. `CodeSubTask`
3. `WorktreeSession`
4. `SubAgentCodingResult`
5. `IntegrationResult`

### 7.2 Application 层

职责：

- 判断任务是否适合并行编码。
- 生成代码子任务计划。
- 调用 worktree 设施创建隔离工作区。
- 调度子 Agent 执行编码任务。
- 收集结果并组织集成流程。

建议新增服务：

1. `ParallelCodingPlanner`
2. `ParallelCodingOrchestrator`
3. `SubAgentCodingExecutionService`
4. `IntegrationCoordinator`

### 7.3 Infra 层

职责：

- 调用本地 Git 命令。
- 创建 / 清理 worktree。
- 获取仓库状态、分支状态、提交信息。
- 组织本地 merge 或 cherry-pick。

建议新增适配器：

1. `GitWorktreeProvisioningService`
2. `LocalGitRepositoryInspector`
3. `LocalGitIntegrationService`

---

## 8. 关键组件职责

### 8.1 `GitWorktreeProvisioningService`

职责：

1. 检查 Git 是否可用。
2. 检查当前目录是否为 Git 仓库。
3. 创建 worktree。
4. 删除 worktree。
5. 创建或绑定分支。
6. 查询 worktree 列表和状态。
7. 为失败任务做清理回收。

它不负责：

1. 不负责任务拆分。
2. 不负责模型推理。
3. 不负责 GitHub 登录。
4. 不负责复杂业务决策。

### 8.2 `ParallelCodingPlanner`

职责：

1. 判断任务是否适合并行编码。
2. 输出子任务边界。
3. 给出每个子任务的文件范围、验证命令、风险等级。

### 8.3 `SubAgentCodingExecutionService`

职责：

1. 将子任务映射到指定 worktree。
2. 让子 Agent 在该 worktree 中独立执行。
3. 收集结构化执行结果。

### 8.4 `IntegrationCoordinator`

职责：

1. 创建集成分支。
2. 按顺序合并或 cherry-pick 子任务结果。
3. 检测冲突。
4. 触发集成验证。
5. 输出最终结果报告。

---

## 9. Git 命令能力范围

第一版建议封装以下 Git 操作：

1. `git --version`
2. `git rev-parse --is-inside-work-tree`
3. `git branch --show-current`
4. `git rev-parse HEAD`
5. `git status --short`
6. `git worktree list --porcelain`
7. `git worktree add <path> -b <branch> <baseRef>`
8. `git worktree remove <path> --force`
9. `git add ...`
10. `git commit -m ...`
11. `git checkout -b <branch>`
12. `git cherry-pick <sha>`
13. `git merge --no-ff <branch>`

说明：

- 第一版优先支持本地命令闭环。
- 远程命令如 `git push` 不进入当前必做范围。

---

## 10. 子任务拆分规则建议

并行编码能否成功，关键不在“并发数”，而在“拆分质量”。

第一版建议主 Agent 强制输出以下字段：

1. `title`
2. `goal`
3. `ownedPaths`
4. `forbiddenPaths`
5. `verificationCommands`
6. `dependsOn`
7. `conflictRisk`

并行准入规则建议：

1. `dependsOn` 为空。
2. `ownedPaths` 基本不重叠。
3. `conflictRisk` 不高。
4. 每个子任务都具备最小可验证命令。

不满足时回退单 Agent。

---

## 11. 执行流程建议

第一版推荐固定成以下流程：

1. 用户提交编码任务。
2. 主 Agent 评估是否适合并行。
3. 若不适合，则走单 Agent。
4. 若适合，则生成多个 `CodeSubTask`。
5. 为每个子任务创建独立 worktree 与分支。
6. 子 Agent 在各自 worktree 中执行：
   - 阅读代码
   - 修改代码
   - 运行限定验证
   - 生成结果摘要
   - 可选自动提交 commit
7. 主 Agent 收集全部结果。
8. 创建集成分支。
9. 顺序 cherry-pick 或 merge 各子结果。
10. 跑集成验证。
11. 输出最终报告。

---

## 12. 合并策略建议

第一版建议优先使用保守策略：

### 12.1 首选：`cherry-pick`

优点：

1. 更可控。
2. 更适合按结果顺序集成。
3. 失败点更明确。

### 12.2 次选：`merge`

适用于：

1. 子任务分支更完整。
2. 需要保留分支结构语义。

### 12.3 冲突策略

第一版不做自动冲突修复。

出现冲突时：

1. 记录冲突分支与文件。
2. 停止后续自动集成。
3. 返回失败状态给主 Agent。
4. 由主 Agent 决定是否提示人工介入或回退单 Agent 收敛。

---

## 13. 结果交付协议建议

子 Agent 的编码结果建议结构化为：

1. `status`
2. `summary`
3. `changedFiles`
4. `branchName`
5. `commitSha`
6. `worktreePath`
7. `testPassed`
8. `testSummary`
9. `errorMessage`

主 Agent 的最终结果建议包含：

1. 总任务状态
2. 每个子任务状态
3. 集成分支名
4. 集成测试结果
5. 成功分支列表
6. 失败分支列表
7. 冲突文件列表

---

## 14. 分步开发计划

以下步骤采用可持续推进方式；完成后在步骤前改为 `[x]`。

### 阶段 A：方案与边界收敛

- [x] 第 1 步：补齐 worktree 并行编码方案文档
  - 明确第一版范围
  - 明确 Git 与 GitHub 的边界
  - 明确回退策略

- [x] 第 2 步：梳理现有 `agentteam` 与新链路的关系
  - 保持当前 `MasterAgentOrchestrator` 不被直接污染
  - 明确新增 `coding` 链路入口

完成标准：

- 方案边界清晰。
- 团队对“第一版只做本地 Git 闭环”达成一致。

### 阶段 B：Git 基础设施能力

- [x] 第 3 步：新增 Git 环境探测能力
  - 检查 `git --version`
  - 检查是否为 Git 仓库
  - 检查当前分支 / HEAD

- [x] 第 4 步：新增 worktree 生命周期适配器
  - 创建 worktree
  - 删除 worktree
  - 查询 worktree 列表
  - 查询分支绑定关系

- [x] 第 5 步：新增基础异常与错误码收口
  - Git 不可用
  - 非 Git 仓库
  - 创建 worktree 失败
  - 清理失败

完成标准：

- 可以独立创建并销毁测试用 worktree。
- 基础失败路径有稳定错误输出。

### 阶段 C：并行编码任务模型

- [x] 第 6 步：定义并行编码领域模型
  - `CodeTaskGroup`
  - `CodeSubTask`
  - `WorktreeSession`
  - `SubAgentCodingResult`
  - `IntegrationResult`

- [x] 第 7 步：定义代码子任务规划输出协议
  - `ownedPaths`
  - `forbiddenPaths`
  - `verificationCommands`
  - `dependsOn`
  - `conflictRisk`

完成标准：

- 模型与现有 `TaskGroup / SubTask` 区分清晰。
- 结果模型以 Git 交付物为中心。

### 阶段 D：子 Agent worktree 执行

- [x] 第 8 步：实现子 Agent 与 worktree 绑定执行
  - 子 Agent 只在指定 worktree 目录下操作
  - 绑定独立 branch / session 信息

- [x] 第 9 步：实现子 Agent 结构化结果收集
  - 记录变更文件
  - 记录 commit
  - 记录测试结果

- [x] 第 10 步：实现子 Agent 自动提交策略
  - 成功时自动 commit
  - 提交信息带任务标识

完成标准：

- 单个子 Agent 可在独立 worktree 中完成一次编码闭环。

### 阶段 E：主 Agent 并行编排

- [x] 第 11 步：实现并行编码任务可拆分判断
  - 不适合拆分时回退单 Agent
  - 适合时生成 2~N 个子任务

- [x] 第 12 步：实现主 Agent 并发调度多个 worktree 子 Agent
  - 创建多个 worktree
  - 并行执行
  - 回收执行结果

- [x] 第 13 步：实现部分失败处理
  - 子任务失败时保留结果
  - 支持终止集成或仅汇总状态

完成标准：

- 主 Agent 能并发驱动多个代码子任务执行。

### 阶段 F：集成与验证

- [x] 第 14 步：实现集成分支创建
  - 基于当前基线创建 integration branch

- [x] 第 15 步：实现顺序 cherry-pick / merge 策略
  - 优先 cherry-pick
  - 冲突时终止并返回详细信息

- [x] 第 16 步：实现集成验证
  - compile
  - 目标测试
  - 汇总测试结果

完成标准：

- 成功子任务可被集成回统一分支。
- 集成后有统一验证结果。

### 阶段 G：接口与可观测性

- [x] 第 17 步：新增并行编码应用服务入口
  - 与当前普通 `agentteam` 协作入口分离
  - 保持默认主链路不受影响

- [x] 第 18 步：补充执行事件与状态输出
  - worktree 创建中
  - 子 Agent 执行中
  - 集成中
  - 成功 / 失败

- [x] 第 19 步：前端补充多 Agent 编码状态展示
  - 每个子任务状态
  - 分支 / worktree / 测试结果
  - 集成结果

完成标准：

- 用户可感知每个 Agent 在做什么。

### 阶段 H：测试与回归

- [x] 第 20 步：补 Git worktree 基础设施测试
  - 环境探测
  - worktree 创建 / 清理
  - 错误路径

- [x] 第 21 步：补并行编码链路测试
  - 单子任务
  - 多子任务
  - 部分失败
  - 冲突终止

- [ ] 第 22 步：补单 Agent 回归测试
  - 默认执行路径不受影响

完成标准：

- worktree 模式有稳定自动化验证。
- 默认单 Agent 路径无回退。

---

## 15. 第一版验收标准

### 功能验收

1. 可检测当前环境是否支持 Git worktree 模式。
2. 可创建至少 2 个独立 worktree。
3. 每个子 Agent 可在独立 worktree 中完成修改与本地验证。
4. 可输出结构化编码结果。
5. 主 Agent 可集成多个成功结果。
6. 冲突与失败路径可被识别和上报。

### 架构验收

1. 默认单 Agent 链路不回退。
2. `agentteam` 新能力通过清晰接口接入。
3. 不把 Git 逻辑散落到 Controller。
4. 不把 worktree 协作逻辑下沉为过度抽象框架。

### 测试验收

1. `compile` 通过。
2. 默认 `test` 通过。
3. 新增 worktree 基础能力测试通过。
4. 新增并行编码 smoke test 通过。

---

## 16. 后续增强方向

第一版稳定后，再评估以下增强：

1. 支持 push 到远程分支。
2. 支持 GitHub PR 自动创建。
3. 支持更细粒度冲突预测。
4. 支持更强的文件隔离与权限控制。
5. 支持 DAG 型编码任务调度。
6. 支持更丰富的协作消息协议。

---

## 17. 当前结论

参考 Claude Code 的方向，`agentteam` 下一阶段最合理的演进路径不是继续强化“共享工作区并发”，而是：

**将多 Agent 编码能力收敛为“Git worktree + 独立分支 + 子 Agent 隔离执行 + 主 Agent 本地集成”的最小可运行闭环。**

当前建议的第一步不是立刻大规模改代码，而是：

1. 先补齐 Git 基础设施与错误边界。
2. 再补 worktree 绑定的子 Agent 执行。
3. 再补主 Agent 并行编排与本地集成。

这样能在不破坏现有单 Agent 主链路的前提下，稳步推进到“多 Agent 并行写代码”。
