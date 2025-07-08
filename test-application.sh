#!/bin/bash

echo "OpenManus-Java 应用测试脚本"
echo "========================="
echo ""

# 检查端口
echo "1. 检查端口占用..."
lsof -i :8088

# 启动应用
echo ""
echo "2. 启动应用..."
mvn spring-boot:run &
APP_PID=$!

# 等待应用启动
echo ""
echo "3. 等待应用启动..."
sleep 20

# 检查应用状态
echo ""
echo "4. 检查应用状态..."
curl -s http://localhost:8088/ -o /dev/null -w "HTTP Status: %{http_code}\n"

# 测试WebSocket连接
echo ""
echo "5. 测试WebSocket连接..."
curl -s -N -H "Connection: Upgrade" -H "Upgrade: websocket" -H "Sec-WebSocket-Version: 13" -H "Sec-WebSocket-Key: SGVsbG8sIHdvcmxkIQ==" http://localhost:8088/ws/agent 2>&1 | head -5

# 测试API端点
echo ""
echo "6. 测试API端点..."
curl -s http://localhost:8088/api/agent/status | jq . 2>/dev/null || echo "API端点不可用"

# 停止应用
echo ""
echo "7. 停止应用..."
kill $APP_PID 2>/dev/null

echo ""
echo "测试完成！" 