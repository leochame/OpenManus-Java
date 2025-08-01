# Agent 执行流程监控系统

## 概述

新的 Agent 执行流程监控系统专注于可视化展示 Agent 的执行过程，特别是 Think-Do-Reflect 工作流中的思考、执行、反思各个阶段。系统提供了详细的输入输出跟踪、LLM 交互记录和工具调用监控。

## 系统架构

### 后端组件

1. **AgentExecutionTracker** - 核心跟踪服务
   - 跟踪 Agent 执行事件
   - 记录详细执行流程
   - 管理 LLM 交互和工具调用

2. **DetailedExecutionFlow** - 详细执行流程模型
   - 工作流状态管理
   - 执行阶段跟踪
   - LLM 交互记录
   - 工具调用记录

3. **AgentMonitoringController** - 监控 API 控制器
   - 提供监控数据查询接口
   - 支持实时数据获取

4. **MonitoringTestController** - 测试控制器
   - 模拟 Think-Do-Reflect 工作流
   - 用于演示监控功能

### 前端组件

1. **agent-monitoring.html** - 主监控页面
   - 实时仪表板
   - 执行流程列表
   - 详细流程可视化
   - Think-Do-Reflect 执行界面

## 主要功能

### 1. 实时监控仪表板

- **总体统计**: 活跃会话数、总执行流程数
- **流程状态**: 运行中、已完成、失败的流程统计
- **成功率**: 计算执行成功率

### 2. 详细执行流程跟踪

每个执行流程包含：

- **基本信息**: 会话ID、用户输入、开始/结束时间、总持续时间
- **执行阶段**: 思考、执行、反思等各个阶段的详细信息
- **输入输出**: 每个阶段的输入和输出数据
- **LLM 交互**: 请求内容、响应内容、使用的模型、Token 消耗、响应时间
- **工具调用**: 调用的工具、参数、结果、执行时间

### 3. 可视化展示

- **流程列表**: 显示最近的执行流程，支持状态筛选
- **详细视图**: 点击流程可查看完整的执行详情
- **阶段展示**: 不同类型的阶段用不同颜色标识
  - 🧠 思考阶段 (紫色)
  - ⚡ 执行阶段 (蓝色)  
  - 🤔 反思阶段 (橙色)

### 4. Think-Do-Reflect 集成

- **直接执行**: 在监控页面直接执行 Think-Do-Reflect 工作流
- **实时跟踪**: 执行过程中实时更新监控数据
- **结果展示**: 执行完成后自动显示详细流程

## API 接口

### 监控数据接口

```
GET /api/agent-monitoring/dashboard
GET /api/agent-monitoring/sessions/active
GET /api/agent-monitoring/sessions/{sessionId}/events
GET /api/agent-monitoring/sessions/{sessionId}/detailed-flow
GET /api/agent-monitoring/flows/all
GET /api/agent-monitoring/flows/recent
GET /api/agent-monitoring/sessions/{sessionId}/stats
DELETE /api/agent-monitoring/flows/cleanup
```

### 测试接口

```
POST /api/agent-monitor-test/simulate-think-do-reflect
GET /api/agent-monitor-test/session/{sessionId}/status
DELETE /api/agent-monitor-test/cleanup
```

## 使用方法

### 1. 访问监控页面

访问 `http://localhost:8089/pages/agent-monitoring.html` 打开监控页面。

### 2. 查看实时数据

页面会每5秒自动刷新监控数据，显示最新的执行状态。

### 3. 执行测试工作流

1. 点击 "🧠 启动Think-Do-Reflect" 按钮
2. 输入测试任务内容
3. 点击 "🚀 执行工作流" 开始执行
4. 系统会实时跟踪执行过程

### 4. 查看详细流程

1. 在执行流程列表中点击任意流程
2. 系统会展开显示详细的执行信息
3. 包括每个阶段的输入输出、LLM交互、工具调用等

### 5. 模拟测试

在主页面点击 "监控测试" 功能卡片，系统会：
1. 自动启动一个模拟的 Think-Do-Reflect 工作流
2. 3秒后自动跳转到监控页面
3. 可以观察到完整的执行过程

## 数据模型

### DetailedExecutionFlow

```java
- sessionId: 会话ID
- userInput: 用户输入
- startTime/endTime: 开始/结束时间
- totalDuration: 总持续时间
- status: 工作流状态 (RUNNING/COMPLETED/FAILED/CANCELLED)
- finalResult: 最终结果
- phases: 执行阶段列表
```

### ExecutionPhase

```java
- phaseId: 阶段ID
- phaseName: 阶段名称
- phaseType: 阶段类型 (THINKING/EXECUTION/REFLECTION/TOOL_USAGE/DECISION)
- agentName/agentType: 执行的Agent信息
- startTime/endTime/duration: 时间信息
- input/output: 输入输出数据
- llmInteractions: LLM交互记录
- toolCalls: 工具调用记录
```

### LLMInteraction

```java
- interactionId: 交互ID
- requestTime/responseTime: 请求/响应时间
- request/response: 请求/响应内容
- model: 使用的模型
- tokenUsage: Token使用情况
- responseTime_ms: 响应时长
```

### ToolCall

```java
- callId: 调用ID
- toolName: 工具名称
- callTime/completionTime: 调用/完成时间
- parameters/result: 参数/结果
- status: 调用状态
- duration: 执行时长
```

## 清理和维护

### 自动清理

系统提供自动清理功能，可以删除指定时间之前的已完成流程：

```java
agentExecutionTracker.cleanupCompletedFlows(24); // 清理24小时前的流程
```

### 手动清理

在监控页面点击 "🗑️ 清理旧流程" 按钮，可以手动清理24小时前的已完成流程。

## 扩展性

系统设计具有良好的扩展性：

1. **新的阶段类型**: 可以轻松添加新的 PhaseType
2. **自定义监控**: 可以在任何 Agent 中添加监控跟踪
3. **数据导出**: 支持监控数据的导出和分析
4. **实时通知**: 可以扩展支持 WebSocket 实时推送

## 注意事项

1. **性能考虑**: 监控数据会占用内存，建议定期清理
2. **并发安全**: 使用了线程安全的数据结构
3. **错误处理**: 监控失败不会影响 Agent 的正常执行
4. **数据持久化**: 当前数据存储在内存中，重启后会丢失

## 未来改进

1. **数据持久化**: 支持数据库存储
2. **WebSocket 支持**: 实时推送监控数据
3. **更多图表**: 添加执行时间趋势、成功率变化等图表
4. **告警功能**: 支持执行失败或超时告警
5. **性能分析**: 提供性能瓶颈分析功能
