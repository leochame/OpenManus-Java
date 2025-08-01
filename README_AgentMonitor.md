# Agent执行监控系统 - 使用指南

## 🎯 系统概述

Agent执行监控系统是一个人类友好的界面，用于实时监控和展示Agent的执行状态。它提供了直观的可视化界面，让开发者能够清楚地看到：

- **当前执行到了哪个Agent**
- **每个Agent的输入是什么**
- **每个Agent的输出是什么**
- **执行状态和耗时信息**
- **错误信息和调试详情**

## 🚀 快速开始

### 1. 启动应用
```bash
# 启动Spring Boot应用
./mvnw spring-boot:run
```

### 2. 访问演示页面
打开浏览器访问：`http://localhost:8080/agent-monitor-demo.html`

### 3. 模拟Agent执行
点击页面上的按钮来模拟不同的Agent执行场景：
- **模拟Think-Do-Reflect流程**：完整的思考-执行-反思循环
- **模拟多Agent协作**：多个Agent协同工作
- **模拟错误处理**：展示错误处理和恢复机制

### 4. 查看执行监控
执行开始后，系统会自动打开监控界面，实时显示Agent执行状态。

## 📱 界面功能

### 主监控界面 (`/agent-execution-monitor.html`)

#### 左侧面板 - 执行流程
- **时间线视图**：按时间顺序显示所有Agent执行事件
- **状态指示器**：
  - 🟡 黄色圆点：正在执行
  - 🟢 绿色圆点：执行成功
  - 🔴 红色圆点：执行失败
  - ⚪ 灰色圆点：等待执行
- **执行统计**：显示总事件数、Agent数量、成功次数、总耗时

#### 右侧面板 - 详细信息
- **Agent信息**：名称、类型、状态
- **时间信息**：开始时间、结束时间、执行耗时
- **输入数据**：Agent接收的输入参数
- **输出数据**：Agent产生的输出结果
- **错误信息**：如果执行失败，显示详细错误
- **元数据**：额外的执行上下文信息

### 演示界面 (`/agent-monitor-demo.html`)

#### 模拟执行区域
- **Think-Do-Reflect流程**：模拟完整的智能体工作流
- **多Agent协作**：模拟多个Agent协同执行任务
- **错误处理流程**：模拟错误发生和恢复过程

#### 活跃会话管理
- **会话列表**：显示当前所有活跃的执行会话
- **会话详情**：每个会话的基本信息和状态
- **会话操作**：查看详情、清除会话等操作

## 🔧 技术架构

### 后端组件

1. **AgentExecutionEvent**：执行事件数据模型
2. **AgentExecutionTracker**：执行状态跟踪服务
3. **AgentExecutionController**：REST API接口
4. **AgentMonitorTestController**：测试和演示接口
5. **TrackedAgentExecutor**：Agent执行装饰器

### 前端组件

1. **agent-execution-monitor.html**：主监控界面
2. **agent-monitor-demo.html**：演示和测试界面
3. **Vue.js + Element Plus**：响应式UI框架

## 🛠️ API接口

### 获取会话执行事件
```http
GET /api/agent-execution/sessions/{sessionId}/events
```

### 获取活跃会话
```http
GET /api/agent-execution/sessions/active
```

### 获取会话统计信息
```http
GET /api/agent-execution/sessions/{sessionId}/statistics
```

### 清理会话数据
```http
DELETE /api/agent-execution/sessions/{sessionId}
```

### 模拟执行（测试用）
```http
POST /api/agent-monitor-test/simulate/think-do-reflect
POST /api/agent-monitor-test/simulate/multi-agent
POST /api/agent-monitor-test/simulate/error-flow
```

## 📋 使用场景

### 1. 开发调试
- 实时查看Agent执行流程
- 快速定位执行问题
- 分析性能瓶颈

### 2. 系统监控
- 监控生产环境Agent状态
- 跟踪执行成功率
- 分析执行时间分布

### 3. 演示展示
- 向客户展示Agent工作原理
- 教学和培训用途
- 系统功能演示

## 🎨 界面特色

### 人类友好设计
- **直观的时间线**：清晰展示执行顺序
- **颜色编码状态**：一目了然的状态指示
- **详细信息面板**：完整的执行上下文
- **实时更新**：自动刷新最新状态

### 响应式布局
- **桌面优化**：大屏幕下的最佳体验
- **移动适配**：手机和平板设备支持
- **弹性布局**：适应不同屏幕尺寸

## 🔍 监控数据

### 执行事件类型
- `AGENT_START`：Agent开始执行
- `AGENT_END`：Agent执行结束
- `TOOL_CALL`：工具调用事件
- `ERROR`：错误事件
- `HANDOFF`：Agent交接

### 执行状态
- `PENDING`：等待执行
- `RUNNING`：正在执行
- `SUCCESS`：执行成功
- `FAILED`：执行失败
- `CANCELLED`：执行取消
- `TIMEOUT`：执行超时

## 🚨 故障排除

### 监控数据不显示
1. 检查会话ID是否正确
2. 确认后端服务正常运行
3. 检查浏览器控制台错误

### 实时更新不工作
1. 检查网络连接
2. 确认API接口可访问
3. 检查浏览器JavaScript设置

### 性能问题
1. 定期清理历史会话数据
2. 调整自动刷新频率
3. 限制显示的事件数量

## 🎯 最佳实践

### 会话管理
- 为每次执行分配唯一会话ID
- 定期清理过期会话数据
- 合理设置会话超时时间

### 监控策略
- 关注关键Agent的执行状态
- 设置合理的刷新频率
- 重点监控错误率和执行时间

### 调试技巧
- 利用详细信息面板分析问题
- 对比成功和失败的执行流程
- 关注工具调用的输入输出

## 📈 扩展功能

### 可能的增强
- WebSocket实时推送
- 数据持久化存储
- 监控告警机制
- 执行性能分析
- 自定义监控面板

### 集成建议
- 与日志系统集成
- 添加监控指标收集
- 支持自定义事件类型
- 提供监控API SDK

## 🎉 总结

Agent执行监控系统提供了一个完整的解决方案来监控和调试Agent执行过程。通过直观的界面设计和丰富的功能特性，它能够帮助开发者：

✅ **快速理解**Agent执行流程  
✅ **及时发现**执行问题和错误  
✅ **有效分析**性能和瓶颈  
✅ **轻松演示**系统功能特性  

立即开始使用，体验人类友好的Agent监控界面！
