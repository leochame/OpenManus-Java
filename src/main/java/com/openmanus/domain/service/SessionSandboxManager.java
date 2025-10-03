package com.openmanus.domain.service;

import com.openmanus.domain.model.SessionSandboxInfo;
import com.openmanus.infra.sandbox.VncSandboxClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话沙箱管理器
 * 
 * 职责：
 * 1. 管理 sessionId 与沙箱容器的映射关系
 * 2. 按需创建沙箱（首次调用浏览器工具时）
 * 3. 提供沙箱信息查询接口
 * 4. 定期清理过期的沙箱容器
 * 
 * 设计模式：
 * - 单例模式：全局唯一的会话管理器
 * - 工厂模式：创建沙箱实例
 * - 缓存模式：内存缓存会话-沙箱映射
 */
@Service
@Slf4j
public class SessionSandboxManager {
    
    private final VncSandboxClient vncSandboxClient;
    
    // 会话沙箱映射表 - 线程安全
    private final Map<String, SessionSandboxInfo> sessionSandboxMap = new ConcurrentHashMap<>();
    
    // 沙箱超时时间（小时）
    private static final int SANDBOX_TIMEOUT_HOURS = 2;
    
    @Autowired
    public SessionSandboxManager(VncSandboxClient vncSandboxClient) {
        this.vncSandboxClient = vncSandboxClient;
        log.info("SessionSandboxManager 初始化完成");
    }
    
    /**
     * 获取会话的沙箱信息（如果存在）
     * 
     * @param sessionId 会话 ID
     * @return 沙箱信息，不存在则返回 empty
     */
    public Optional<SessionSandboxInfo> getSandboxInfo(String sessionId) {
        SessionSandboxInfo info = sessionSandboxMap.get(sessionId);
        
        if (info != null) {
            // 验证容器是否仍在运行
            if (!vncSandboxClient.isContainerRunning(info.getContainerId())) {
                log.warn("会话 {} 的沙箱容器已停止，更新状态", sessionId);
                info.setStatus(SessionSandboxInfo.SandboxStatus.STOPPED);
            }
        }
        
        return Optional.ofNullable(info);
    }
    
    /**
     * 为会话创建或获取沙箱
     * 
     * @param sessionId 会话 ID
     * @return 沙箱信息
     */
    public synchronized SessionSandboxInfo getOrCreateSandbox(String sessionId) {
        // 检查是否已存在
        SessionSandboxInfo existing = sessionSandboxMap.get(sessionId);
        if (existing != null && existing.isAvailable()) {
            log.debug("复用现有沙箱: sessionId={}, vncUrl={}", sessionId, existing.getVncUrl());
            return existing;
        }
        
        // 创建新沙箱
        log.info("为会话 {} 创建新的 VNC 沙箱", sessionId);
        
        try {
            // 标记为创建中
            SessionSandboxInfo creatingInfo = SessionSandboxInfo.builder()
                .sessionId(sessionId)
                .status(SessionSandboxInfo.SandboxStatus.CREATING)
                .createdAt(LocalDateTime.now())
                .build();
            sessionSandboxMap.put(sessionId, creatingInfo);
            
            // 调用 VncSandboxClient 创建容器
            VncSandboxClient.VncSandboxInfo vncInfo = vncSandboxClient.createVncSandbox(sessionId);
            
            // 构建会话沙箱信息
            SessionSandboxInfo sandboxInfo = SessionSandboxInfo.builder()
                .sessionId(sessionId)
                .containerId(vncInfo.getContainerId())
                .vncUrl(vncInfo.getVncUrl())
                .mappedPort(vncInfo.getMappedPort())
                .status(SessionSandboxInfo.SandboxStatus.RUNNING)
                .createdAt(LocalDateTime.now())
                .build();
            
            // 存入映射表
            sessionSandboxMap.put(sessionId, sandboxInfo);
            
            log.info("会话 {} 的 VNC 沙箱创建成功: {}", sessionId, sandboxInfo.getVncUrl());
            return sandboxInfo;
            
        } catch (Exception e) {
            log.error("创建 VNC 沙箱失败: sessionId={}", sessionId, e);
            
            // 标记为错误状态
            SessionSandboxInfo errorInfo = SessionSandboxInfo.builder()
                .sessionId(sessionId)
                .status(SessionSandboxInfo.SandboxStatus.ERROR)
                .createdAt(LocalDateTime.now())
                .build();
            sessionSandboxMap.put(sessionId, errorInfo);
            
            throw new RuntimeException("创建 VNC 沙箱失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 销毁会话的沙箱
     * 
     * @param sessionId 会话 ID
     */
    public void destroySandbox(String sessionId) {
        SessionSandboxInfo info = sessionSandboxMap.remove(sessionId);
        
        if (info != null && info.getContainerId() != null) {
            try {
                log.info("销毁会话 {} 的沙箱容器: {}", sessionId, info.getContainerId());
                vncSandboxClient.destroyVncSandbox(info.getContainerId());
            } catch (Exception e) {
                log.error("销毁沙箱失败: sessionId={}, containerId={}", 
                    sessionId, info.getContainerId(), e);
            }
        }
    }
    
    /**
     * 定期清理过期的沙箱容器
     * 每 30 分钟执行一次
     */
    @Scheduled(fixedRate = 30 * 60 * 1000)  // 30 分钟
    public void cleanupExpiredSandboxes() {
        log.info("开始清理过期的沙箱容器");
        
        LocalDateTime now = LocalDateTime.now();
        int cleanedCount = 0;
        
        for (Map.Entry<String, SessionSandboxInfo> entry : sessionSandboxMap.entrySet()) {
            SessionSandboxInfo info = entry.getValue();
            
            if (info.getCreatedAt() != null) {
                long hours = ChronoUnit.HOURS.between(info.getCreatedAt(), now);
                
                // 清理超过 2 小时的沙箱
                if (hours >= SANDBOX_TIMEOUT_HOURS) {
                    log.info("清理过期沙箱: sessionId={}, 运行时间={}小时", 
                        entry.getKey(), hours);
                    destroySandbox(entry.getKey());
                    cleanedCount++;
                }
            }
        }
        
        if (cleanedCount > 0) {
            log.info("清理完成，共清理 {} 个过期沙箱", cleanedCount);
        } else {
            log.debug("无需清理，所有沙箱都在有效期内");
        }
    }
    
    /**
     * 获取当前活跃的沙箱数量
     */
    public int getActiveSandboxCount() {
        return (int) sessionSandboxMap.values().stream()
            .filter(SessionSandboxInfo::isAvailable)
            .count();
    }
    
    /**
     * 应用关闭时清理所有沙箱
     */
    @PreDestroy
    public void cleanup() {
        log.info("应用关闭，清理所有沙箱容器");
        
        for (String sessionId : sessionSandboxMap.keySet()) {
            destroySandbox(sessionId);
        }
        
        log.info("所有沙箱已清理完成");
    }
}

