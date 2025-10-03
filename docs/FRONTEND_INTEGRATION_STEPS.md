# 前端集成实施步骤

## 📋 当前状态

### ✅ 已完成
- 后端代码 100% 完成
- API 接口已就绪
- 详细的技术文档

### ❌ 待完成
- **前端代码修改** - `src/main/resources/static/index.html` 还未修改
- **环境配置** - Docker 需要启动

---

## 🔧 环境配置

### 1. 启动 Docker

```bash
# macOS (使用 Docker Desktop)
open -a Docker

# 或者命令行启动（如果已安装 Docker Desktop）
# 等待 Docker Desktop 启动完成，再执行：
docker ps
```

### 2. 预拉取 VNC 镜像（可选但推荐）

```bash
# 这个镜像约 1GB，提前拉取可以减少首次使用的等待时间
docker pull dorowu/ubuntu-desktop-lxde-vnc:latest
```

---

## 💻 前端代码修改

由于前端是单文件 Vue 应用（1000+ 行），我为您提供两种集成方式：

### 方式一：最小化修改（推荐，快速验证）

只需要在现有代码中添加 3 个部分：

#### 步骤 1: 在 `<style>` 标签中添加 CSS（约第 800 行附近）

```css
/* === 在现有样式最后添加 === */

/* 主布局 - 支持左右分栏 */
.main-layout {
    display: flex;
    gap: 16px;
    height: calc(100vh - 200px);
}

.chat-panel {
    flex: 1;
    min-width: 400px;
}

/* 沙箱面板样式 */
.sandbox-panel {
    width: 45%;
    min-width: 400px;
    background: white;
    border-radius: var(--border-radius);
    box-shadow: var(--shadow-lg);
    display: flex;
    flex-direction: column;
    overflow: hidden;
}

.sandbox-header {
    padding: 16px 20px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.sandbox-title {
    font-size: 16px;
    font-weight: 600;
}

.sandbox-body {
    flex: 1;
    position: relative;
}

.sandbox-iframe {
    width: 100%;
    height: 100%;
    border: none;
}

@media (max-width: 1200px) {
    .main-layout {
        flex-direction: column;
    }
    .sandbox-panel {
        width: 100%;
        height: 400px;
    }
}
```

#### 步骤 2: 修改 HTML 结构（约第 890 行）

将这段代码：
```html
<div class="main-content">
    <div class="chat-container">
        <!-- 现有的聊天容器内容 -->
    </div>
</div>
```

替换为：
```html
<div class="main-content">
    <div class="main-layout">
        <!-- 左侧：聊天面板 -->
        <div class="chat-panel">
            <div class="chat-container">
                <!-- 保持原有的所有聊天容器内容不变 -->
            </div>
        </div>
        
        <!-- 右侧：沙箱浏览器面板 -->
        <div class="sandbox-panel" v-if="showSandboxPanel">
            <div class="sandbox-header">
                <span class="sandbox-title">🖥️ Agent 浏览器工作台</span>
                <el-button size="small" type="info" @click="showSandboxPanel = false">
                    关闭
                </el-button>
            </div>
            <div class="sandbox-body">
                <iframe 
                    v-if="sandboxVncUrl"
                    :src="sandboxVncUrl" 
                    class="sandbox-iframe"
                    title="Agent Browser Workspace"
                ></iframe>
                <div v-else style="text-align: center; padding: 80px 20px;">
                    <el-icon :size="40" class="is-loading"><Loading /></el-icon>
                    <p style="margin-top: 16px; color: #666;">正在启动浏览器工作台...</p>
                </div>
            </div>
        </div>
    </div>
</div>
```

#### 步骤 3: 在 Vue setup() 中添加沙箱逻辑（约第 1057 行）

在 `setup()` 函数的开始部分添加：

```javascript
// 在现有的 ref 声明后添加
const sandboxVncUrl = ref(null);
const showSandboxPanel = ref(false);
let sandboxPollInterval = null;

// 添加轮询函数
const pollSessionInfo = async (sessionId) => {
    try {
        const response = await fetch(`/api/agent/session/${sessionId}`);
        if (response.ok) {
            const data = await response.json();
            
            if (data.sandboxVncUrl && data.sandboxAvailable) {
                sandboxVncUrl.value = data.sandboxVncUrl;
                showSandboxPanel.value = true;
                
                // 停止轮询
                if (sandboxPollInterval) {
                    clearInterval(sandboxPollInterval);
                    sandboxPollInterval = null;
                }
                
                ElMessage.success('✅ 浏览器工作台已就绪');
                addDebugInfo(`沙箱URL: ${data.sandboxVncUrl}`);
            }
        }
    } catch (error) {
        console.error('查询会话信息失败:', error);
        addDebugInfo(`查询沙箱失败: ${error.message}`);
    }
};

const startSandboxPolling = (sessionId) => {
    addDebugInfo(`开始轮询沙箱状态: ${sessionId}`);
    pollSessionInfo(sessionId);
    
    sandboxPollInterval = setInterval(() => {
        pollSessionInfo(sessionId);
    }, 3000);
    
    // 最多轮询 20 次（1 分钟）
    setTimeout(() => {
        if (sandboxPollInterval) {
            clearInterval(sandboxPollInterval);
            sandboxPollInterval = null;
            addDebugInfo('沙箱轮询超时，停止轮询');
        }
    }, 60000);
};
```

#### 步骤 4: 修改 sendThinkDoReflect 函数（约第 1300 行）

在 WebSocket 连接成功后添加轮询启动：

```javascript
const sendThinkDoReflect = async () => {
    // ... 现有代码 ...
    
    try {
        const response = await fetch('/api/agent/think-do-reflect-stream', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ input: userMessage })
        });
        
        if (response.ok) {
            const data = await response.json();
            conversationId.value = data.sessionId;
            
            // ✨ 新增：启动沙箱轮询
            startSandboxPolling(data.sessionId);
            
            // ... 其余现有代码 ...
        }
    } catch (error) {
        // ... 错误处理 ...
    }
};
```

#### 步骤 5: 在 return 语句中添加新变量（约第 1450 行）

```javascript
return {
    // ... 现有的返回值 ...
    sandboxVncUrl,
    showSandboxPanel,
    // ... 其余现有返回值 ...
};
```

---

### 方式二：使用我提供的完整修改脚本（更稳妥）

如果您不想手动修改，我可以为您创建一个完整的新版本 `index.html` 文件。

**需要您确认**：
1. 是否需要我直接修改 `index.html` 文件？
2. 或者您想先手动按照上述步骤修改？

---

## 🧪 测试步骤

### 1. 启动 Docker
```bash
open -a Docker
# 等待启动完成
docker ps
```

### 2. 启动应用
```bash
./mvnw spring-boot:run
```

### 3. 访问前端
```
http://localhost:8089
```

### 4. 测试沙箱功能
在聊天框输入：
```
帮我搜索最新的 AI 技术新闻
```

**预期结果**：
- 约 5-10 秒后，右侧出现"Agent 浏览器工作台"面板
- iframe 中显示 VNC 桌面环境
- 可以看到浏览器自动打开

---

## ❓ 常见问题

### Q1: Docker 启动失败怎么办？
```bash
# macOS: 确保 Docker Desktop 已安装并启动
open -a Docker

# 检查状态
docker info
```

### Q2: VNC 面板不出现？
- 检查浏览器控制台是否有错误
- 查看后端日志：是否有 Docker 相关错误
- 验证 API 接口：访问 `http://localhost:8089/api/agent/session/test`

### Q3: iframe 显示空白？
- 检查 VNC URL 是否正确
- 验证 Docker 容器是否启动：`docker ps | grep vnc`
- 检查端口是否被占用

---

## 📞 下一步

请告诉我您希望：

**选项 A**: 我手动按照上述步骤修改 ✋  
**选项 B**: 请帮我直接修改 `index.html` 文件 🤖  
**选项 C**: 我先配置 Docker 环境，稍后再处理前端 🐳

我会根据您的选择继续协助您完成集成！

