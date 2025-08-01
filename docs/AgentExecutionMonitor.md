# Agent执行监控系统

## 概述

Agent执行监控系统是一个人类友好的界面，用于实时监控和展示Agent的执行状态。它提供了详细的执行流程可视化，包括每个Agent的输入、输出、执行时间和状态信息。

## 核心功能

### 1. 实时执行监控
- **执行流程可视化**: 以时间线形式展示Agent执行顺序
- **状态跟踪**: 实时显示每个Agent的执行状态（等待中、执行中、成功、失败）
- **输入输出展示**: 详细显示每个Agent的输入参数和输出结果
- **执行时间统计**: 记录和显示每个Agent的执行耗时

### 2. 会话管理
- **会话隔离**: 每次执行都有独立的会话ID
- **历史记录**: 保存执行历史，支持回溯查看
- **会话统计**: 提供执行统计信息（总事件数、成功率、总耗时等）

### 3. 错误处理和调试
- **错误信息展示**: 详细显示执行过程中的错误信息
- **调试支持**: 提供详细的执行上下文信息
- **状态恢复**: 支持从错误状态恢复执行

## 使用指南

### 1. 启动监控

#### 方式一：通过Think-Do-Reflect界面
1. 访问 `http://localhost:8080/think-do-reflect.html`
2. 输入任务并执行
3. 执行完成后，点击"查看执行过程"按钮

#### 方式二：直接访问监控界面
1. 访问 `http://localhost:8080/agent-execution-monitor.html`
2. 输入会话ID并点击"加载"按钮

#### 方式三：通过演示界面
1. 访问 `http://localhost:8080/agent-monitor-demo.html`
2. 点击"模拟Agent执行"按钮
3. 自动跳转到监控界面

### 2. 监控界面说明

#### 执行流程面板
- **时间线视图**: 按时间顺序显示Agent执行事件
- **状态指示器**: 不同颜色表示不同的执行状态
  - 🟡 黄色：正在执行
  - 🟢 绿色：执行成功
  - 🔴 红色：执行失败
  - ⚪ 灰色：等待执行
- **执行时间**: 显示每个Agent的执行耗时

#### 详细信息面板
- **Agent信息**: 显示Agent名称、类型、状态
- **时间信息**: 显示开始时间、结束时间、执行耗时
- **输入输出**: 显示Agent的输入参数和输出结果
- **错误信息**: 如果执行失败，显示详细错误信息
- **元数据**: 显示额外的执行上下文信息

#### 统计信息
- **总事件数**: 会话中的总事件数量
- **Agent数量**: 参与执行的Agent数量
- **成功次数**: 成功执行的事件数量
- **总耗时**: 整个会话的总执行时间

### 3. API接口

#### 获取会话事件
```bash
GET /api/agent-execution/sessions/{sessionId}/events
```

#### 获取活跃会话
```bash
GET /api/agent-execution/sessions/active
```

#### 获取会话统计
```bash
GET /api/agent-execution/sessions/{sessionId}/statistics
```

#### 清理会话数据
```bash
DELETE /api/agent-execution/sessions/{sessionId}
```

## 系统架构

### 后端组件

1. **AgentExecutionEvent**: 执行事件模型
2. **AgentExecutionTracker**: 执行跟踪服务
3. **AgentExecutionController**: REST API控制器
4. **TrackedAgentExecutor**: Agent执行器装饰器

### 前端组件

1. **agent-execution-monitor.html**: 主监控界面
2. **agent-monitor-demo.html**: 演示和测试界面

## 集成方式

### 为现有Agent添加监控

```java
@Autowired
private TrackedAgentExecutor.TrackedAgentFactory trackedAgentFactory;

// 包装Agent添加监控功能
Map.Entry<ToolSpecification, ToolExecutor> trackedAgent = 
    trackedAgentFactory.createTrackedAgent(originalAgent);
```

### 自定义事件监听器

```java
@Component
public class CustomEventListener implements AgentExecutionTracker.AgentExecutionEventListener {
    @Override
    public void onEvent(AgentExecutionEvent event) {
        // 处理Agent执行事件
    }
}
```

## 特性优势

1. **人类友好**: 直观的界面设计，易于理解和使用
2. **实时监控**: 实时显示执行状态和进度
3. **详细信息**: 提供完整的执行上下文和调试信息
4. **易于集成**: 最小化侵入性，易于集成到现有系统
5. **可扩展性**: 支持自定义扩展和功能增强

通过这个监控系统，开发者可以更好地理解Agent的执行过程，快速定位问题，优化系统性能。
