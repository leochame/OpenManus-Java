# 循环次数配置指南

## 📋 概述

OpenManus 中有多个与循环和迭代相关的配置参数，本指南将详细说明每个参数的作用以及如何修改它们。

## 🔧 主要配置参数

### 1. 工作流最大步骤数 (`runflow.max-steps`)

**位置**：`src/main/resources/application.yaml`

```yaml
runflow:
  enabled: false
  max-steps: 20        # 默认值：20
  timeout: 300         # 超时时间（秒），默认：300
```

**作用**：
- 控制 Think-Do-Reflect 工作流的最大循环步骤数
- 防止无限循环，保护系统资源
- 每个"思考-执行-反思"循环计为多个步骤

**推荐值**：
- **轻量任务**：10-15 步
- **普通任务**：20-30 步（默认）
- **复杂任务**：40-50 步
- **极限任务**：100 步（需要同时增加 timeout）

### 2. 对话记忆最大消息数 (`MAX_MEMORY_MESSAGES`)

**位置**：`src/main/java/com/openmanus/infra/config/LangChain4jConfig.java`

```java
private static final int MAX_MEMORY_MESSAGES = 50;  // 默认值：50
```

**作用**：
- 控制对话历史保留的最大消息数
- 影响上下文窗口大小
- 防止 token 超限

**推荐值**：
- **短对话**：20-30 条
- **正常对话**：50-100 条（默认）
- **长对话**：200-300 条
- **超长对话**：500 条（需要注意 token 限制）

### 3. LLM 最大 Token 数 (`llm.max-tokens`)

**位置**：`src/main/resources/application.yaml`

```yaml
llm:
  default-llm:
    max-tokens: 8192   # 默认值：8192
    timeout: 120       # 超时时间（秒）
```

**作用**：
- 控制单次 LLM 调用的最大 token 数
- 影响生成的最大长度
- 与模型上限相关

**推荐值**：
- **快速响应**：2048-4096 tokens
- **正常使用**：8192 tokens（默认）
- **长文本**：16384-32768 tokens
- **极限生成**：128000 tokens（模型支持的话）

### 4. 工具超时时间

#### 沙箱超时
```yaml
sandbox:
  timeout: 120  # 默认：120 秒
```

#### 浏览器超时
```yaml
browser:
  timeout: 30   # 默认：30 秒
```

#### LLM 超时
```yaml
llm:
  default-llm:
    timeout: 120  # 默认：120 秒
```

## 🎯 常见场景配置

### 场景 1：快速响应（牺牲完整性）

```yaml
runflow:
  max-steps: 10
  timeout: 180

llm:
  default-llm:
    max-tokens: 4096
    timeout: 60
```

```java
// LangChain4jConfig.java
private static final int MAX_MEMORY_MESSAGES = 30;
```

**适用于**：简单查询、快速测试

---

### 场景 2：标准配置（平衡）

```yaml
runflow:
  max-steps: 20
  timeout: 300

llm:
  default-llm:
    max-tokens: 8192
    timeout: 120
```

```java
// LangChain4jConfig.java
private static final int MAX_MEMORY_MESSAGES = 50;
```

**适用于**：大多数日常任务（默认）

---

### 场景 3：复杂任务（追求完整性）

```yaml
runflow:
  max-steps: 50
  timeout: 600

llm:
  default-llm:
    max-tokens: 16384
    timeout: 180

sandbox:
  timeout: 300
```

```java
// LangChain4jConfig.java
private static final int MAX_MEMORY_MESSAGES = 100;
```

**适用于**：复杂研究、多步骤分析、代码生成

---

### 场景 4：极限任务（无限可能）

```yaml
runflow:
  max-steps: 100
  timeout: 1800

llm:
  default-llm:
    max-tokens: 32768
    timeout: 300

sandbox:
  timeout: 600

browser:
  timeout: 120
```

```java
// LangChain4jConfig.java
private static final int MAX_MEMORY_MESSAGES = 200;
```

**适用于**：大型项目、深度分析、长时间研究

## 📝 修改步骤

### 方法 1：修改配置文件（推荐）

1. **编辑 `application.yaml`**

```bash
vim src/main/resources/application.yaml
```

找到并修改：
```yaml
runflow:
  enabled: false
  max-steps: 30      # 改为你想要的值
  timeout: 600       # 改为你想要的值

llm:
  default-llm:
    max-tokens: 16384  # 改为你想要的值
    timeout: 180       # 改为你想要的值
```

2. **编辑 `LangChain4jConfig.java`**

```bash
vim src/main/java/com/openmanus/infra/config/LangChain4jConfig.java
```

修改第 28 行：
```java
private static final int MAX_MEMORY_MESSAGES = 100;  // 改为你想要的值
```

3. **重新编译和启动**

```bash
mvn clean package
mvn spring-boot:run
```

---

### 方法 2：使用环境变量（灵活）

在启动时传入参数：

```bash
java -jar target/openmanus-*.jar \
  --openmanus.runflow.max-steps=50 \
  --openmanus.runflow.timeout=600 \
  --openmanus.llm.default-llm.max-tokens=16384 \
  --openmanus.llm.default-llm.timeout=180
```

或设置环境变量：

```bash
export OPENMANUS_RUNFLOW_MAXSTEPS=50
export OPENMANUS_RUNFLOW_TIMEOUT=600
export OPENMANUS_LLM_DEFAULTLLM_MAXTOKENS=16384
export OPENMANUS_LLM_DEFAULTLLM_TIMEOUT=180

mvn spring-boot:run
```

---

### 方法 3：创建自定义配置文件

创建 `application-custom.yaml`：

```yaml
openmanus:
  runflow:
    max-steps: 50
    timeout: 600
  llm:
    default-llm:
      max-tokens: 16384
      timeout: 180
```

启动时指定：

```bash
mvn spring-boot:run -Dspring.profiles.active=custom
```

## ⚠️ 注意事项

### 1. 性能影响

- **增加 `max-steps`**：
  - ✅ 可以处理更复杂的任务
  - ❌ 响应时间变长
  - ❌ 消耗更多 API 调用

- **增加 `MAX_MEMORY_MESSAGES`**：
  - ✅ 更长的上下文记忆
  - ❌ 每次调用消耗更多 token
  - ❌ 可能超过模型的上下文限制

- **增加 `max-tokens`**：
  - ✅ 可以生成更长的内容
  - ❌ 每次调用更贵
  - ❌ 响应时间变长

### 2. 成本考虑

假设使用 OpenAI GPT-4：

| 配置 | 每任务估计 Tokens | 成本估算 |
|------|-------------------|----------|
| 轻量（10 步）| ~50K tokens | $1.5 |
| 标准（20 步）| ~100K tokens | $3.0 |
| 复杂（50 步）| ~250K tokens | $7.5 |
| 极限（100 步）| ~500K tokens | $15.0 |

### 3. 系统资源

- **超时时间**应该大于 `max-steps × 平均每步时间`
- **内存**：增加消息数会增加内存占用
- **网络**：考虑网络延迟和稳定性

### 4. 模型限制

不同模型有不同的上下文窗口限制：

| 模型 | 最大上下文 | 建议 max-tokens |
|------|-----------|-----------------|
| GPT-3.5 | 16K | 4096-8192 |
| GPT-4 | 128K | 8192-32768 |
| Claude 3 | 200K | 8192-65536 |
| Qwen (通义千问) | 8K-32K | 4096-16384 |

## 🧪 测试建议

修改配置后，建议进行以下测试：

### 1. 简单任务测试

```
问：北京今天天气怎么样？
预期：1-3 步完成
```

### 2. 中等任务测试

```
问：帮我搜索 Vue 3 最佳实践，并总结要点
预期：5-10 步完成
```

### 3. 复杂任务测试

```
问：研究 Rust 编程语言的优缺点，对比 C++ 和 Go，生成详细报告
预期：15-30 步完成
```

### 4. 压力测试

```
问：分析 GitHub 上所有流行的 AI 框架，对比功能、性能、生态，生成评测报告
预期：测试 max-steps 上限
```

## 📊 监控和调优

### 查看实际使用情况

在日志中查找：

```bash
grep "工作流完成" logs/*.log
grep "执行步骤" logs/*.log
grep "token" logs/*.log
```

### 性能指标

- **平均步骤数**：理想值应该低于 `max-steps` 的 60%
- **超时率**：应该低于 5%
- **成功率**：应该高于 95%

### 优化建议

如果发现：
- **经常达到 max-steps 上限** → 增加 max-steps 或优化提示词
- **经常超时** → 增加 timeout 或减少 max-steps
- **内存不足** → 减少 MAX_MEMORY_MESSAGES
- **成本过高** → 减少 max-tokens 或使用更便宜的模型

## 🎯 推荐配置速查表

| 使用场景 | max-steps | timeout | max-tokens | MAX_MEMORY |
|---------|-----------|---------|-----------|------------|
| 开发测试 | 10 | 180 | 4096 | 30 |
| 日常使用 | 20 | 300 | 8192 | 50 |
| 生产环境 | 30 | 600 | 16384 | 100 |
| 研究分析 | 50 | 1200 | 32768 | 200 |
| 极限挑战 | 100 | 1800 | 65536 | 300 |

## 🔍 故障排查

### 问题 1：任务总是在中途停止

**原因**：达到了 `max-steps` 限制

**解决**：增加 `max-steps` 值

---

### 问题 2：系统响应很慢

**原因**：`max-tokens` 或 `MAX_MEMORY_MESSAGES` 过大

**解决**：减少这些值，或者升级到更快的模型

---

### 问题 3：经常超时

**原因**：`timeout` 设置过小

**解决**：增加各个 `timeout` 配置

---

### 问题 4：成本过高

**原因**：循环次数过多或 token 消耗过大

**解决**：
- 减少 `max-steps`
- 减少 `max-tokens`
- 优化提示词
- 使用更便宜的模型

## 📚 相关文档

- [OpenManusProperties.java](../src/main/java/com/openmanus/infra/config/OpenManusProperties.java) - 配置类定义
- [LangChain4jConfig.java](../src/main/java/com/openmanus/infra/config/LangChain4jConfig.java) - LangChain4j 配置
- [application.yaml](../src/main/resources/application.yaml) - 主配置文件

---

**最后更新**：2025-10-04  
**版本**：v1.0.0
