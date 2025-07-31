# Think-Do-Reflect 多智能体协作系统使用指南

## 系统概述

Think-Do-Reflect系统是基于OpenManus框架实现的循环反思型多智能体协作系统，采用"想、做、回"三步工作模式：

- **想 (Think)**: 分析任务，制定执行计划
- **做 (Do)**: 执行具体任务（搜索、代码、文件操作等）
- **回 (Reflect)**: 评估结果，决定是否需要继续改进

## 核心组件

### 1. 智能体类型

- **SupervisorAgent**: 总协调者，管理整个工作流程
- **ThinkingAgent**: 任务分析和规划专家
- **SearchAgent**: 信息检索专家
- **CodeAgent**: 代码执行专家
- **FileAgent**: 文件操作专家
- **ReflectionAgent**: 结果评估专家

### 2. 工具系统

- **AgentToolbox**: 将所有智能体包装为工具
- **AgentToolCatalog**: 管理工具集合

## 使用方法

### 1. REST API 调用

```bash
# 执行Think-Do-Reflect工作流
curl -X POST http://localhost:8080/api/think-do-reflect/execute \
  -H "Content-Type: application/json" \
  -d '{"input": "分析最近的AI发展趋势并生成报告"}'

# 健康检查
curl http://localhost:8080/api/think-do-reflect/health
```

### 2. 编程方式调用

```java
@Autowired
private ThinkDoReflectWorkflow workflow;

public void executeTask() {
    String userInput = "分析最近的AI发展趋势并生成报告";
    
    // 异步执行
    CompletableFuture<String> result = workflow.execute(userInput);
    result.thenAccept(response -> {
        System.out.println("执行结果: " + response);
    });
    
    // 流式执行（支持进度追踪）
    workflow.executeWithProgress(userInput)
        .forEach(progress -> {
            System.out.println("进度: " + progress);
        });
}
```

## 工作流程示例

### 示例1: 信息分析任务

**用户输入**: "分析2024年AI创业公司的融资情况"

**执行流程**:
1. **Think**: 分析任务，制定计划（搜索相关信息 → 数据分析 → 生成报告）
2. **Do**: 使用SearchAgent搜索融资信息，CodeAgent进行数据分析
3. **Reflect**: 评估结果完整性，如不满足则重新规划
4. **循环**: 根据反思结果决定是否需要补充信息或改进分析

### 示例2: 代码开发任务

**用户输入**: "开发一个计算斐波那契数列的Python函数并测试"

**执行流程**:
1. **Think**: 分析需求（编写函数 → 编写测试 → 执行验证）
2. **Do**: 使用CodeAgent编写函数和测试代码，执行验证
3. **Reflect**: 检查代码质量和测试结果
4. **循环**: 如有问题则优化代码或补充测试

## 配置说明

### 1. 必需配置

```yaml
openmanus:
  langchain4j:
    chat-model:
      provider: openai  # 或其他支持的提供商
      openai:
        api-key: ${OPENAI_API_KEY}
        model-name: gpt-4
```

### 2. 可选配置

```yaml
# 循环控制
think-do-reflect:
  max-cycles: 5  # 最大循环次数
  
# 工具配置
tools:
  python:
    enabled: true
    timeout: 30s
  browser:
    enabled: true
    timeout: 10s
  file:
    enabled: true
    base-path: /tmp/openmanus
```

## 最佳实践

### 1. 任务描述

- **具体明确**: 提供详细的任务描述和期望结果
- **分步骤**: 对于复杂任务，可以分解为多个子任务
- **设定标准**: 明确完成标准，帮助反思智能体做出准确判断

### 2. 错误处理

- 系统会自动处理执行错误并重新规划
- 达到最大循环次数时会返回当前最佳结果
- 可以通过日志监控执行过程

### 3. 性能优化

- 合理设置最大循环次数
- 对于简单任务，可以考虑直接使用单个智能体
- 监控执行时间，避免过度循环

## 扩展开发

### 1. 添加新的执行智能体

```java
@Component
public class CustomAgent extends AbstractAgentExecutor<CustomAgent.Builder> {
    // 实现自定义智能体逻辑
}

// 在AgentToolbox中添加对应的工具方法
@Tool("自定义任务执行")
public String executeCustomTask(String task) {
    return customAgent.execute(request, null);
}
```

### 2. 自定义反思逻辑

可以通过修改ReflectionAgent的系统提示来调整评估标准和反思逻辑。

## 故障排除

### 1. 常见问题

- **循环次数过多**: 检查任务描述是否过于模糊，调整反思标准
- **执行失败**: 检查工具配置和API密钥
- **响应缓慢**: 考虑使用更快的模型或优化任务分解

### 2. 日志分析

系统会记录详细的执行日志，包括：
- 每个智能体的调用情况
- 循环次数和状态变化
- 工具执行结果
- 错误信息和异常堆栈

通过分析日志可以了解系统的执行过程和性能瓶颈。
