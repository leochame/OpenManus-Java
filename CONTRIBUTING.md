# 贡献指南

感谢您对 OpenManus Java 项目的关注！我们欢迎各种形式的贡献，包括但不限于代码贡献、文档改进、bug报告和功能建议。

## 🤝 如何贡献

### 报告 Bug

在报告 bug 之前，请先检查 [现有的 Issues](https://github.com/OpenManus/OpenManus-Java/issues) 确保问题尚未被报告。

创建 bug 报告时，请包含以下信息：

- 使用清晰、描述性的标题
- 详细描述重现步骤
- 提供具体的示例代码或配置
- 说明期望的行为和实际行为
- 包含环境信息（Java版本、操作系统等）
- 如果可能，提供错误日志或截图

### 功能请求

我们欢迎新功能的建议！请先检查 [Issues](https://github.com/OpenManus/OpenManus-Java/issues) 确保功能尚未被建议。

创建功能请求时，请：

- 使用清晰、描述性的标题
- 详细描述建议的功能
- 解释为什么这个功能对用户有价值
- 如果可能，提供实现的想法

### 代码贡献

#### 开发环境设置

1. **Fork 项目**
```bash
# 在 GitHub 上点击 Fork 按钮，然后克隆您的 fork
git clone https://github.com/YOUR_USERNAME/OpenManus-Java.git
cd OpenManus-Java
```

2. **设置开发环境**
```bash
# 安装 Java 21
brew install openjdk@21  # macOS
# 或者从 https://adoptium.net/ 下载

# 安装 Maven
brew install maven       # macOS
# 或者从 https://maven.apache.org/ 下载

# 安装 Docker
brew install docker      # macOS
# 或者安装 Docker Desktop
```

3. **配置远程仓库**
```bash
git remote add upstream https://github.com/OpenManus/OpenManus-Java.git
```

4. **验证环境**
```bash
java --version
mvn --version
docker --version
```

#### 开发流程

1. **创建功能分支**
```bash
git checkout -b feature/your-feature-name
```

2. **进行开发**
- 遵循现有的代码风格
- 添加适当的注释
- 编写或更新测试
- 确保所有测试通过

3. **运行测试**
```bash
# 运行所有测试
./run-tests.sh

# 或者使用 Maven
mvn clean test
```

4. **提交更改**
```bash
git add .
git commit -m "feat: add amazing new feature"
```

5. **推送更改**
```bash
git push origin feature/your-feature-name
```

6. **创建 Pull Request**
- 在 GitHub 上创建 PR
- 使用清晰的标题和描述
- 关联相关的 Issues
- 等待代码审查

## 📝 代码规范

### Java 代码风格

- 使用 4 个空格缩进
- 类名使用 PascalCase
- 方法和变量名使用 camelCase
- 常量使用 UPPER_SNAKE_CASE
- 包名使用小写字母

### 注释规范

```java
/**
 * 类的简要描述
 * 
 * @author Your Name
 * @since 1.0.0
 */
public class ExampleClass {
    
    /**
     * 方法的简要描述
     * 
     * @param param 参数描述
     * @return 返回值描述
     * @throws Exception 异常描述
     */
    public String exampleMethod(String param) throws Exception {
        // 代码实现
    }
}
```

### 提交信息规范

我们使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

#### 类型 (type)

- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档更改
- `style`: 代码格式更改（不影响代码运行的变动）
- `refactor`: 重构（既不是新增功能，也不是修改bug的代码变动）
- `test`: 增加测试
- `chore`: 构建过程或辅助工具的变动

#### 示例

```
feat(agent): add new reasoning algorithm
fix(llm): resolve null pointer exception in token estimation
docs(readme): update installation instructions
```

## 🧪 测试

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=LlmClientTest

# 运行集成测试
mvn verify
```

### 编写测试

- 为新功能编写单元测试
- 确保测试覆盖率 > 80%
- 使用描述性的测试方法名
- 遵循 AAA 模式（Arrange, Act, Assert）

```java
@Test
void shouldReturnCorrectTokenCountWhenEstimatingTokens() {
    // Arrange
    String input = "Hello, world!";
    LlmClient client = new LlmClient();
    
    // Act
    int tokenCount = client.estimateTokens(input);
    
    // Assert
    assertEquals(3, tokenCount);
}
```

## 📋 Pull Request 检查清单

在提交 PR 之前，请确保：

- [ ] 代码符合项目的代码风格
- [ ] 添加了适当的测试
- [ ] 所有测试都通过
- [ ] 更新了相关文档
- [ ] 提交信息符合规范
- [ ] PR 描述清晰，关联了相关 Issues
- [ ] 没有引入新的警告或错误
- [ ] 考虑了向后兼容性

## 🏗️ 项目结构

```
src/
├── main/
│   ├── java/com/openmanus/java/
│   │   ├── agent/          # AI代理实现
│   │   ├── config/         # 配置类
│   │   ├── llm/            # LLM客户端
│   │   ├── sandbox/        # Docker沙箱
│   │   ├── tool/           # 工具集合
│   │   └── model/          # 数据模型
│   └── resources/
│       └── application.yml # 应用配置
└── test/                   # 测试代码
```

## 🎯 开发优先级

当前开发重点：

1. **核心功能稳定性** - 修复已知bug，提高系统稳定性
2. **工具扩展** - 添加新的工具支持
3. **性能优化** - 提高响应速度和资源利用率
4. **文档完善** - 改进用户和开发者文档
5. **测试覆盖** - 提高测试覆盖率

## 🆘 获取帮助

如果您在贡献过程中遇到问题，可以通过以下方式获取帮助：

- 在 [GitHub Discussions](https://github.com/OpenManus/OpenManus-Java/discussions) 中提问
- 查看 [Issues](https://github.com/OpenManus/OpenManus-Java/issues) 中的相关讨论
- 阅读项目文档和代码注释

## 📜 行为准则

参与此项目的每个人都应遵守以下准则：

- 尊重所有参与者
- 接受建设性的批评
- 专注于对社区最有利的事情
- 对其他社区成员表现出同理心

## 📞 联系我们

- 项目维护者：OpenManus Team
- 邮箱：team@openmanus.io
- GitHub：[@OpenManus](https://github.com/OpenManus)

感谢您的贡献！🎉 