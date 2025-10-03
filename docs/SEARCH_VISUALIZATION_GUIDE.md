# 搜索可视化功能使用指南

## 🎯 功能概述

OpenManus 现在支持**完整的搜索过程可视化**，让您能够实时看到：

1. 🔍 **搜索过程**：Agent 在搜索引擎上搜索的过程
2. 📄 **搜索结果页面**：DuckDuckGo 搜索结果的完整网页
3. 🌐 **访问的网页**：从搜索结果中访问的具体网页

---

## 🚀 快速体验

### 测试用例 1：纯搜索

```
帮我搜索最新的人工智能技术
```

**预期效果**：
1. ⚡ 右侧面板立即出现
2. 🔍 标题显示"搜索结果"
3. 📄 展示 DuckDuckGo 的搜索结果页面
4. 🔗 标题栏显示完整搜索 URL

### 测试用例 2：搜索 + 访问

```
帮我搜索 OpenAI 的官网，然后访问它
```

**预期效果**：
1. 🔍 首先显示搜索结果页面
2. 🌐 然后切换到 OpenAI 官网页面
3. 📄 可以看到两次 URL 变化
4. 🔄 随时可以切换到 VNC 查看完整操作

### 测试用例 3：直接访问网页

```
帮我访问 https://github.com/trending
```

**预期效果**：
1. 🌐 标题显示"网页预览"
2. 📄 直接展示 GitHub Trending 页面
3. 🔗 标题栏显示 github.com URL

---

## 🎨 界面说明

### 面板标题

#### 搜索结果模式
```
🔍 搜索结果  https://html.duckduckgo.com/html/?q=...
```

#### 网页预览模式
```
🌐 网页预览  https://github.com
```

#### VNC 沙箱模式
```
🖥️ VNC 沙箱
```

### 控制按钮

| 按钮 | 图标 | 功能 | 显示条件 |
|------|------|------|---------|
| **在新标签页打开** | ⬆️ | 在浏览器新标签页中打开当前网页 | 网页预览模式 |
| **切换到VNC** | 按钮 | 切换到完整的 VNC 沙箱环境 | VNC 已就绪 |
| **切换到网页** | 按钮 | 切换回轻量级的网页预览 | VNC 模式下 |
| **关闭** | 按钮 | 隐藏整个沙箱面板 | 始终显示 |

---

## 💡 工作原理

### 搜索流程

```
用户输入搜索请求
    ↓
Agent 调用 searchWeb("关键词")
    ↓
后端输出: "🔍 正在搜索: 关键词"
后端输出: "📄 搜索页面: https://..."
    ↓
前端提取 URL
    ↓
在右侧 iframe 中展示搜索结果
```

### URL 提取逻辑

前端会智能识别以下格式的日志：

1. **搜索页面 URL**
   ```
   📄 搜索页面: https://html.duckduckgo.com/html/?q=xxx
   ```

2. **访问网页 URL**
   ```
   📄 正在访问: https://github.com
   ```

3. **搜索关键词**（作为备选）
   ```
   🔍 正在搜索: 人工智能
   → 前端自动构建: https://html.duckduckgo.com/html/?q=人工智能
   ```

### 动态标题

```javascript
// 判断是否为搜索引擎
if (currentWebUrl.includes('duckduckgo.com')) {
    标题 = "🔍 搜索结果"
} else {
    标题 = "🌐 网页预览"
}
```

---

## 🔍 支持的搜索引擎

### 当前支持

- ✅ **DuckDuckGo**
  - URL: `https://html.duckduckgo.com/html/?q=xxx`
  - 特点：无广告、隐私友好、支持 iframe 嵌入
  - 自动识别并显示"搜索结果"标题

### 未来扩展

可以通过修改 `BrowserTool.java` 支持其他搜索引擎：

```java
// 添加更多搜索引擎
private static final String GOOGLE_SEARCH_URL = "https://www.google.com/search?q=";
private static final String BING_SEARCH_URL = "https://www.bing.com/search?q=";
```

**注意**：Google 和 Bing 可能有 iframe 嵌入限制，需要使用 VNC 模式。

---

## 🎯 使用场景

### 场景 1：快速查找信息

**用户需求**：快速了解某个话题

**操作**：
```
帮我搜索量子计算的最新进展
```

**优势**：
- ⚡ 秒级响应
- 📄 直接看到搜索结果列表
- 🔍 可以立即判断信息相关性

### 场景 2：深入研究

**用户需求**：深入研究特定网站

**操作**：
```
帮我搜索 React 18 新特性，然后访问官方文档
```

**优势**：
- 📊 先看到搜索结果全景
- 🎯 然后深入具体页面
- 🔄 随时切换到 VNC 进行复杂操作

### 场景 3：对比信息

**用户需求**：对比多个信息源

**操作**：
```
帮我搜索 TypeScript vs JavaScript 的对比，访问前3个结果
```

**优势**：
- 📋 在搜索页面看到所有候选结果
- 🔗 Agent 依次访问多个网页
- 👀 你可以在右侧实时查看每个页面

---

## ⚠️ 注意事项

### iframe 嵌入限制

某些网站不允许被 iframe 嵌入，会显示空白：

**受限网站**：
- ❌ Google.com (X-Frame-Options: DENY)
- ❌ Facebook.com
- ❌ Twitter.com
- ❌ Instagram.com
- ❌ 银行网站（安全策略）

**支持网站**：
- ✅ DuckDuckGo（搜索引擎）
- ✅ GitHub.com
- ✅ Wikipedia.org
- ✅ Stack Overflow
- ✅ MDN Web Docs
- ✅ 大部分技术文档网站

**解决方案**：
1. 点击"在新标签页打开"按钮
2. 或点击"切换到VNC"使用完整浏览器

### 搜索结果的点击

在网页预览模式下，点击搜索结果链接可能：
- ✅ 在同一 iframe 中打开（大部分情况）
- ❌ 被阻止（受安全策略限制）
- 🆕 在新标签页打开（取决于网站实现）

**推荐做法**：
- 让 Agent 自动访问搜索结果
- 或切换到 VNC 模式手动点击

---

## 🐛 故障排查

### 问题 1：搜索页面显示空白

**症状**：右侧面板打开但内容空白

**可能原因**：
1. 网络连接问题
2. DuckDuckGo 临时不可访问
3. iframe 安全策略问题

**解决方案**：
```bash
# 1. 检查浏览器控制台（F12）
# 查看是否有错误信息

# 2. 查看调试面板
点击右下角 "🔧" 按钮 → 查看 URL 是否正确提取

# 3. 尝试在新标签页打开
点击 "⬆️" 按钮 → 验证 URL 是否可访问

# 4. 切换到 VNC 模式
点击 "切换到VNC" → 使用完整浏览器
```

### 问题 2：URL 没有被提取

**症状**：搜索后右侧面板不出现

**排查步骤**：
```javascript
// 1. 打开浏览器控制台（F12）
// 2. 查看 WebSocket 日志
// 应该能看到类似的消息：
// "🔍 正在搜索: xxx"
// "📄 搜索页面: https://..."

// 3. 检查 URL 提取函数
// 在控制台执行：
console.log(currentWebUrl.value);
console.log(showSandboxPanel.value);
```

### 问题 3：搜索结果不完整

**症状**：只显示部分搜索结果

**原因**：DuckDuckGo HTML 版本的限制

**解决方案**：
- 切换到 VNC 模式
- Agent 会解析搜索结果并返回文本摘要
- 两者结合使用，获得最完整的信息

---

## 🔧 高级配置

### 自定义搜索引擎

```java
// src/main/java/com/openmanus/agent/tool/BrowserTool.java

// 修改搜索引擎 URL（第 52 行）
private static final String SEARCH_ENGINE_URL = "https://www.bing.com/search?q=";

// 记得同时修改前端识别逻辑
```

### 修改默认行为

```javascript
// src/main/resources/static/index.html

// 默认使用 VNC 模式而非网页预览
sandboxMode.value = 'vnc';  // 修改第 1208 行

// 禁用自动 URL 提取
// 注释掉第 1543-1545 行
// extractAndShowWebUrl(log.message);
```

### 添加更多识别模式

```javascript
// 识别 Google 搜索
if (message.includes('google.com/search')) {
    标题 = "🔍 Google 搜索"
}

// 识别 Bing 搜索
if (message.includes('bing.com/search')) {
    标题 = "🔍 Bing 搜索"
}
```

---

## 📊 对比：搜索前 vs 搜索后

| 特性 | 优化前 | 优化后 |
|------|--------|--------|
| **搜索过程** | ❌ 不可见 | ✅ 实时展示 |
| **搜索结果** | 📝 纯文本 | 🌐 完整网页 |
| **用户体验** | 🤔 想象 | 👀 实际看到 |
| **信息丰富度** | ⭐ 摘要 | ⭐⭐⭐ 完整页面 |
| **交互性** | ❌ 无 | ✅ 可点击、可浏览 |

---

## 🎓 最佳实践

### 1. 先搜索，再访问

```
✅ 好：帮我搜索 React Hooks 教程，选择最好的一个访问
❌ 差：帮我访问 React Hooks 教程
```

**原因**：
- 能看到搜索结果全景
- Agent 可以判断最佳选择
- 你能了解 Agent 的决策过程

### 2. 结合文本和可视化

```
Agent 会：
1. 在聊天中显示文本摘要（快速理解）
2. 在右侧显示完整网页（详细查看）
```

**最佳组合**：
- 📝 左侧：阅读 Agent 的分析和总结
- 🌐 右侧：查看原始网页验证信息

### 3. 按需切换模式

```
简单浏览 → 网页预览（快速）
复杂操作 → VNC 沙箱（完整）
受限网站 → 新标签页打开（绕过限制）
```

---

## 📚 相关文档

- [DUAL_MODE_SANDBOX_GUIDE.md](./DUAL_MODE_SANDBOX_GUIDE.md) - 双模式详细说明
- [COMPLETE_INTEGRATION_SUMMARY.md](./COMPLETE_INTEGRATION_SUMMARY.md) - 完整功能总结
- [SANDBOX_DEPLOYMENT_GUIDE.md](./SANDBOX_DEPLOYMENT_GUIDE.md) - 部署指南

---

## 🎉 总结

搜索可视化功能让 OpenManus 的透明度提升到了新的层次：

✅ **完整可见**：搜索 → 结果 → 访问，全程可视化  
✅ **实时响应**：秒级展示，无需等待  
✅ **智能识别**：自动区分搜索和普通浏览  
✅ **灵活控制**：多种模式，按需切换  
✅ **用户友好**：清晰的 UI，直观的操作  

现在就开始体验吧！🚀

```bash
./mvnw spring-boot:run
# 访问 http://localhost:8089
# 输入：帮我搜索量子计算的最新进展
# 观察右侧的搜索结果页面！
```

