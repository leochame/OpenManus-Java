# 双模式沙箱面板使用指南

## 🎯 功能概述

OpenManus 现在支持**双模式沙箱面板**，让您可以灵活选择最适合的网页展示方式：

### 🌐 模式 1：网页预览（Web Preview）
- **直接展示网页**：在 iframe 中加载实际网页
- **快速响应**：秒级加载，无需等待容器启动
- **资源轻量**：不需要启动完整的 Docker 容器
- **适用场景**：简单的网页浏览、搜索结果查看

### 🖥️ 模式 2：VNC 沙箱（VNC Sandbox）
- **完整浏览器环境**：Ubuntu 桌面 + Chrome 浏览器
- **复杂交互**：支持鼠标、键盘、多标签页
- **完全隔离**：Docker 容器安全沙箱
- **适用场景**：复杂的网页操作、调试、开发

---

## 🚀 快速开始

### 测试网页预览模式

1. **启动应用**
```bash
./mvnw spring-boot:run
```

2. **访问前端**
```
http://localhost:8089
```

3. **发送测试请求**
```
帮我访问 https://www.github.com
```

4. **观察结果**
- ⚡ 右侧面板立即出现
- 🌐 标题显示"网页预览"
- 📄 iframe 直接加载 GitHub 首页

### 测试 VNC 沙箱模式

1. **等待 VNC 就绪**（约 10-15 秒）
   - 后端会自动创建 VNC 容器
   - 前端轮询检测到 VNC URL

2. **点击切换按钮**
   - 面板右上角"切换到VNC"按钮
   - 🖥️ 标题变为"VNC 沙箱"
   - 可以看到完整的 Ubuntu 桌面

3. **来回切换**
   - 随时点击切换按钮
   - 两种模式可以快速切换
   - 不会丢失浏览状态

---

## 💡 工作原理

### 后端实现

#### BrowserTool 改动
```java
// 访问网页时输出 URL 到日志
log.info(TO_FRONTEND, "📄 正在访问: {}", url);
```

这行日志会通过 WebSocket 实时发送到前端。

### 前端实现

#### 1. URL 提取
```javascript
const extractAndShowWebUrl = (message) => {
    const urlMatch = message.match(/(?:正在访问|访问网页)[:：]\s*(https?:\/\/[^\s]+)/i);
    if (urlMatch && urlMatch[1]) {
        currentWebUrl.value = urlMatch[1];
        showSandboxPanel.value = true;
        sandboxMode.value = 'web';  // 默认网页预览模式
    }
};
```

#### 2. 模式切换
```javascript
const toggleSandboxMode = () => {
    sandboxMode.value = sandboxMode.value === 'web' ? 'vnc' : 'web';
};
```

#### 3. 条件渲染
```html
<!-- 网页预览模式 -->
<div v-if="sandboxMode === 'web'">
    <iframe :src="currentWebUrl"></iframe>
</div>

<!-- VNC 沙箱模式 -->
<div v-else>
    <iframe :src="sandboxVncUrl"></iframe>
</div>
```

---

## 🎨 用户界面

### 面板标题
- 🌐 **网页预览**：蓝色地球图标
- 🖥️ **VNC 沙箱**：电脑图标

### 控制按钮
- **切换模式**：蓝色按钮，显示当前可切换的目标模式
- **关闭**：灰色按钮，隐藏整个面板

### 加载状态
- **网页预览**：简单的加载动画
- **VNC 沙箱**：显示预计等待时间

---

## 🔍 使用场景对比

| 场景 | 推荐模式 | 原因 |
|------|---------|------|
| 查看搜索结果 | 🌐 网页预览 | 快速加载，节省资源 |
| 阅读文章 | 🌐 网页预览 | 简单浏览足够 |
| 填写表单 | 🖥️ VNC 沙箱 | 需要键盘输入 |
| 调试网页 | 🖥️ VNC 沙箱 | 使用开发者工具 |
| 多标签浏览 | 🖥️ VNC 沙箱 | 完整浏览器功能 |
| 下载文件 | 🖥️ VNC 沙箱 | 文件系统访问 |

---

## ⚠️ 注意事项

### 网页预览模式的限制

#### 1. 跨域限制
某些网站禁止被 iframe 嵌入（X-Frame-Options 策略）：
- ❌ Google.com
- ❌ Facebook.com
- ❌ Twitter.com
- ✅ GitHub.com
- ✅ Wikipedia.org

**遇到跨域问题**：
- iframe 会显示空白
- 浏览器控制台报错：`Refused to display in a frame`
- **解决方案**：点击"切换到VNC"使用完整沙箱

#### 2. JavaScript 权限
iframe 的 `sandbox` 属性限制了某些功能：
- ✅ 允许脚本执行
- ✅ 允许表单提交
- ✅ 允许弹窗
- ❌ 限制顶层导航
- ❌ 限制插件运行

#### 3. Cookie 和存储
- 每次加载是独立的会话
- 不保留登录状态
- LocalStorage 受限

### VNC 沙箱模式的优势

所有网页都能正常访问，无跨域限制：
- ✅ 完整的浏览器功能
- ✅ 支持所有网站
- ✅ 可以保持会话状态
- ✅ 支持下载文件
- ⚠️ 需要 10-15 秒启动时间
- ⚠️ 占用约 1GB 内存

---

## 🐛 故障排查

### 问题 1：网页预览显示空白

**可能原因**：
1. 网站禁止 iframe 嵌入（跨域策略）
2. URL 格式不正确
3. 网站需要登录

**解决方案**：
```bash
# 1. 查看浏览器控制台（F12）
# 如果看到 X-Frame-Options 错误，则：
点击"切换到VNC"按钮

# 2. 查看调试面板
# 确认 URL 是否被正确提取
```

### 问题 2：无法切换到 VNC 模式

**可能原因**：VNC 容器尚未创建

**解决方案**：
```bash
# 1. 等待 10-15 秒
# 2. 检查后端日志
grep -i "VNC 沙箱" logs/application.log

# 3. 检查 Docker 容器
docker ps | grep vnc-sandbox

# 4. 如果容器不存在，刷新页面重试
```

### 问题 3：切换模式后内容不更新

**可能原因**：iframe 缓存

**解决方案**：
```javascript
// 关闭面板后重新打开
点击"关闭" → 再次触发浏览操作
```

---

## 🔧 高级配置

### 自定义默认模式

如果您希望默认使用 VNC 模式：

```javascript
// src/main/resources/static/index.html
// 修改第 1208 行
const sandboxMode = ref('vnc');  // 默认 VNC 模式
```

### 禁用网页预览

如果只想使用 VNC 模式：

```javascript
// 注释掉 URL 提取逻辑
// extractAndShowWebUrl(log.message);

// 并移除切换按钮
v-if="false"
```

### 修改 iframe 安全策略

```html
<!-- 更宽松的策略（不推荐） -->
<iframe 
    sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-top-navigation"
></iframe>

<!-- 更严格的策略 -->
<iframe 
    sandbox="allow-scripts"
></iframe>
```

---

## 📊 性能对比

| 指标 | 网页预览 | VNC 沙箱 | 对比 |
|------|---------|---------|------|
| 启动时间 | < 1 秒 | 10-15 秒 | 🌐 快 15x |
| 内存占用 | ~10MB | ~1GB | 🌐 少 100x |
| CPU 占用 | 极低 | 中等 | 🌐 更优 |
| 功能完整性 | 受限 | 完整 | 🖥️ 更强 |
| 兼容性 | 部分网站 | 所有网站 | 🖥️ 更好 |

**结论**：根据实际需求选择合适的模式。

---

## 🎓 最佳实践

### 推荐工作流

1. **首次使用网页预览**
   - 快速查看内容
   - 判断是否需要复杂交互

2. **按需切换到 VNC**
   - 遇到跨域限制时
   - 需要填写表单时
   - 需要多标签浏览时

3. **完成后关闭面板**
   - 节省屏幕空间
   - 下次操作自动重新打开

### 开发调试

```javascript
// 在浏览器控制台查看当前状态
console.log('当前模式:', sandboxMode.value);
console.log('网页 URL:', currentWebUrl.value);
console.log('VNC URL:', sandboxVncUrl.value);
```

---

## 📚 相关文档

- [COMPLETE_INTEGRATION_SUMMARY.md](./COMPLETE_INTEGRATION_SUMMARY.md) - 完整功能总结
- [SANDBOX_DEPLOYMENT_GUIDE.md](./SANDBOX_DEPLOYMENT_GUIDE.md) - 部署指南
- [ENVIRONMENT_SETUP_STATUS.md](./ENVIRONMENT_SETUP_STATUS.md) - 环境配置

---

## 🎉 总结

双模式沙箱面板是 OpenManus 的一个重要改进：

✅ **灵活性**：两种模式自由切换  
✅ **性能**：网页预览快速轻量  
✅ **功能**：VNC 沙箱功能完整  
✅ **用户体验**：智能默认，按需升级  

现在就开始体验吧！🚀

```bash
./mvnw spring-boot:run
# 访问 http://localhost:8089
# 输入：帮我访问 https://www.github.com
```

