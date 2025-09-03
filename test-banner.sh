#!/bin/bash

echo -e "\033[1;34m测试 OpenManusJava 启动Banner...\033[0m"
echo -e "\033[1;34m==================================\033[0m"

# 显示Banner内容
echo -e "\033[1;32mBanner内容预览:\033[0m"
echo -e "\033[1;34m==================================\033[0m"
cat src/main/resources/banner-simple.txt
echo -e "\033[1;34m==================================\033[0m"

echo ""
echo -e "\033[1;32m启动信息预览:\033[0m"
echo -e "\033[1;34m==================================\033[0m"
echo "╭────────────────────────────────────────────────────────────╮"
echo "│                     OpenManusJava                          │"
echo "│                  智能思考系统启动成功!                      │"
echo "╰────────────────────────────────────────────────────────────╯"
echo ""
echo "🌐 Web界面: http://localhost:8089"
echo "📚 API文档: http://localhost:8089/swagger-ui.html"
echo "🔍 健康检查: http://localhost:8089/actuator/health"
echo ""
echo "💡 思考模式:"
echo "   ⚡ 快思考模式 - 直接执行，高效响应，适合简单任务"
echo "   🔍 慢思考模式 - 深度思考，适合复杂任务"
echo "   🤖 自动模式 - 智能选择最佳思考模式"
echo ""
echo "☕ 基于 Java 21 + Spring Boot 3.2.0 + LangChain4j"
echo "╰────────────────────────────────────────────────────────────╯"

echo ""
echo -e "\033[1;33m要启动完整应用，请运行: ./mvnw spring-boot:run\033[0m" 