# 沙箱浏览器前端集成指南

## 概述

本指南说明如何在 OpenManus 前端中集成 VNC 沙箱浏览器展示功能，使用户能够实时看到 Agent 的工作台。

## 实现方案

### 1. 数据获取 Hook

在 Vue 组件的 `setup()` 函数中添加以下代码，实现会话信息的轮询获取：

```javascript
// 在 setup() 函数中添加
const sandboxVncUrl = ref(null);  // 沙箱 VNC URL
const showSandboxPanel = ref(false);  // 是否显示沙箱面板
let sandboxPollInterval = null;  // 轮询定时器

// 轮询获取会话沙箱信息
const pollSessionInfo = async (sessionId) => {
    try {
        const response = await fetch(`/api/agent/session/${sessionId}`);
        if (response.ok) {
            const data = await response.json();
            
            // 如果沙箱可用且之前未显示，则显示面板
            if (data.sandboxVncUrl && data.sandboxAvailable) {
                sandboxVncUrl.value = data.sandboxVncUrl;
                showSandboxPanel.value = true;
                
                // 停止轮询（沙箱已创建）
                if (sandboxPollInterval) {
                    clearInterval(sandboxPollInterval);
                    sandboxPollInterval = null;
                }
                
                ElMessage.success('浏览器工作台已就绪');
            }
        }
    } catch (error) {
        console.error('查询会话信息失败:', error);
    }
};

// 开始轮询沙箱状态（在启动工作流后调用）
const startSandboxPolling = (sessionId) => {
    // 立即查询一次
    pollSessionInfo(sessionId);
    
    // 每 3 秒轮询一次
    sandboxPollInterval = setInterval(() => {
        pollSessionInfo(sessionId);
    }, 3000);
    
    // 最多轮询 20 次（1 分钟），之后停止
    setTimeout(() => {
        if (sandboxPollInterval) {
            clearInterval(sandboxPollInterval);
            sandboxPollInterval = null;
        }
    }, 60000);
};
```

### 2. 修改现有的工作流启动方法

在 `sendMessage` 或启动工作流的方法中，添加轮询启动逻辑：

```javascript
// 修改发送消息的方法
const sendMessage = async (mode = 'stream') => {
    if (!inputMessage.value.trim() || loading.value) return;
    
    const userMessage = inputMessage.value.trim();
    // ... 现有的消息发送逻辑 ...
    
    if (mode === 'stream') {
        try {
            const response = await fetch('/api/agent/think-do-reflect-stream', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ input: userMessage })
            });
            
            if (response.ok) {
                const data = await response.json();
                conversationId.value = data.sessionId;
                
                // 【新增】启动沙箱轮询
                startSandboxPolling(data.sessionId);
                
                // ... 现有的 WebSocket 订阅逻辑 ...
            }
        } catch (error) {
            console.error('发送失败:', error);
        }
    }
};
```

### 3. 添加 CSS 样式

在 `<style>` 标签中添加以下样式：

```css
/* 布局容器 - 支持左右分栏 */
.main-layout {
    display: flex;
    gap: 16px;
    height: calc(100vh - 200px);
    position: relative;
}

/* 聊天面板 */
.chat-panel {
    flex: 1;
    min-width: 400px;
    display: flex;
    flex-direction: column;
    transition: all 0.3s ease;
}

/* 沙箱面板 */
.sandbox-panel {
    width: 50%;
    min-width: 400px;
    background: white;
    border-radius: var(--border-radius);
    box-shadow: var(--shadow-lg);
    display: flex;
    flex-direction: column;
    overflow: hidden;
    position: relative;
}

.sandbox-panel-header {
    padding: 16px 20px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.sandbox-panel-title {
    font-size: 16px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 8px;
}

.sandbox-panel-body {
    flex: 1;
    position: relative;
    overflow: hidden;
}

.sandbox-iframe {
    width: 100%;
    height: 100%;
    border: none;
    background: #f0f0f0;
}

.sandbox-loading {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    text-align: center;
}

/* 响应式调整 */
@media (max-width: 1200px) {
    .main-layout {
        flex-direction: column;
    }
    
    .sandbox-panel {
        width: 100%;
        height: 400px;
    }
}

/* 关闭按钮 */
.close-sandbox-btn {
    background: rgba(255, 255, 255, 0.2);
    border: 1px solid rgba(255, 255, 255, 0.3);
    color: white;
    padding: 4px 12px;
    border-radius: 6px;
    cursor: pointer;
    font-size: 14px;
    transition: all 0.3s;
}

.close-sandbox-btn:hover {
    background: rgba(255, 255, 255, 0.3);
}
```

### 4. 修改 HTML 模板结构

修改聊天容器部分的 HTML，将其包裹在新的布局容器中：

```html
<!-- 原来的 chat-container 部分修改为： -->
<div class="main-content">
    <div class="main-layout">
        <!-- 左侧：聊天面板 -->
        <div class="chat-panel">
            <div class="chat-container">
                <!-- 模式选择区域 -->
                <div class="mode-selector">
                    <!-- 保持原有的模式选择代码 -->
                </div>
                
                <!-- 消息容器 -->
                <div class="messages-container" ref="messagesContainer">
                    <div v-for="(msg, index) in messages" :key="index" :class="['message', msg.role]">
                        <!-- 保持原有的消息显示代码 -->
                    </div>
                </div>
                
                <!-- 输入区域 -->
                <div class="input-container">
                    <!-- 保持原有的输入框代码 -->
                </div>
            </div>
        </div>
        
        <!-- 右侧：沙箱浏览器面板 (条件渲染) -->
        <div class="sandbox-panel" v-if="showSandboxPanel">
            <div class="sandbox-panel-header">
                <div class="sandbox-panel-title">
                    <span>🖥️</span>
                    <span>Agent 浏览器工作台</span>
                </div>
                <button class="close-sandbox-btn" @click="showSandboxPanel = false">
                    关闭
                </button>
            </div>
            <div class="sandbox-panel-body">
                <iframe 
                    v-if="sandboxVncUrl"
                    :src="sandboxVncUrl" 
                    class="sandbox-iframe"
                    title="VNC Sandbox Browser"
                    sandbox="allow-scripts allow-same-origin allow-forms"
                ></iframe>
                <div v-else class="sandbox-loading">
                    <el-icon class="is-loading" :size="40"><Loading /></el-icon>
                    <p style="margin-top: 12px; color: #666;">正在启动浏览器工作台...</p>
                </div>
            </div>
        </div>
    </div>
    
    <!-- 调试面板保持不变 -->
</div>
```

### 5. 组件清理

在组件卸载时清理定时器：

```javascript
// 在 setup() 函数的返回之前添加
onUnmounted(() => {
    // 清理沙箱轮询定时器
    if (sandboxPollInterval) {
        clearInterval(sandboxPollInterval);
    }
});

// 确保返回新添加的响应式变量
return {
    // ... 原有的返回值 ...
    sandboxVncUrl,
    showSandboxPanel,
    startSandboxPolling,
    // ... 其他需要在模板中使用的变量和方法 ...
};
```

## 完整的集成流程

1. **用户发起请求** → 前端调用后端 API 启动工作流
2. **后端返回 sessionId** → 前端收到后立即开始轮询 `/api/agent/session/{sessionId}`
3. **Agent 调用 BrowserTool** → 后端自动创建 VNC 沙箱
4. **前端轮询检测到沙箱** → 获取到 `sandboxVncUrl`，展示右侧面板
5. **用户实时查看** → iframe 中加载 VNC 界面，实时看到 Agent 的浏览操作

## 安全注意事项

1. **iframe sandbox 属性**：使用 `sandbox="allow-scripts allow-same-origin allow-forms"` 限制权限
2. **URL 验证**：确保 VNC URL 来自可信的后端
3. **HTTPS**：生产环境应使用 HTTPS 协议
4. **访问控制**：考虑添加会话验证，防止未授权访问

## 测试步骤

1. 启动后端服务
2. 在浏览器中打开前端页面
3. 发送一个需要浏览网页的请求，例如："帮我访问 https://www.baidu.com"
4. 观察右侧是否出现沙箱面板
5. 检查 iframe 是否正确加载 VNC 界面

## 故障排查

### 沙箱面板未出现
- 检查浏览器控制台是否有网络错误
- 验证 `/api/agent/session/{sessionId}` 接口是否正常返回
- 确认 Docker 容器是否成功启动

### VNC 界面无法加载
- 检查 VNC URL 格式是否正确
- 验证 Docker 端口映射是否正常
- 检查防火墙是否阻止了端口访问

### 性能问题
- 考虑增加 Docker 容器的内存限制
- 优化轮询间隔（可以改为 5 秒）
- 实现 WebSocket 推送代替轮询（高级优化）

## 高级优化

### 使用 WebSocket 推送代替轮询

可以扩展现有的 WebSocket 连接，在沙箱创建成功时主动推送通知：

```javascript
// 在现有的 WebSocket 订阅中添加沙箱状态监听
stompClient.subscribe('/topic/executions/' + sessionId, (message) => {
    const event = JSON.parse(message.body);
    
    // 检查是否有沙箱状态更新
    if (event.eventType === 'SANDBOX_READY' && event.metadata?.sandboxVncUrl) {
        sandboxVncUrl.value = event.metadata.sandboxVncUrl;
        showSandboxPanel.value = true;
        ElMessage.success('浏览器工作台已就绪');
    }
    
    // ... 原有的事件处理逻辑 ...
});
```

后端相应地在沙箱创建成功时发送 WebSocket 事件即可。

