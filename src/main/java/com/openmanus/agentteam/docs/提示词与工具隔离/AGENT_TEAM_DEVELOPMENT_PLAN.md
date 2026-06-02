# Agent Team 开发执行计划

本文档用于跟踪 `agentteam` 提示词隔离、工具隔离与执行入口隔离的开发进展。

## 开发清单

- [x] 第 1 步：角色语义收口
  - 新增 `AgentTeamRole`
  - 明确 `MasterAgentOrchestrator` 的 Team Master 语义
  - 明确 `SubAgentExecutionService` 当前职责和过渡边界

- [x] 第 2 步：提示词入口收口
  - 扩展 `AgentTeamPromptProvider`
  - 增加 Team Master / SubAgent 专用系统提示词模板
  - 保持任务拆解提示词继续独立存在

- [x] 第 3 步：角色化执行入口
  - 新增 `AgentTeamRoleExecutionPort`
  - 新增 `AgentTeamRoleExecutionService`
  - 让 `SubAgentExecutionService` 不再直接依赖默认 `AgentExecutionPort`

- [x] 第 4 步：工具隔离策略
  - 新增 `AgentTeamToolPolicy`
  - 实现 `SubAgentToolPolicy`
  - 实现 `TeamMasterToolPolicy`
  - 提供本地工具注册表，避免在 `agentteam` 内散落工具扫描逻辑

- [x] 第 5 步：角色化 Coordinator 构造
  - 新增 `AgentTeamCoordinatorFactory`
  - 基于角色选择系统提示词和工具集合
  - 先不开放默认 MCP tools

- [x] 第 6 步：配置装配与回归测试
  - 调整 `AgentTeamConfig`
  - 补充 prompt、工具策略、SubAgent 隔离相关测试
  - 验证默认单 Agent 主链路不受影响
