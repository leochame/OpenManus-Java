package com.openmanus.java.agent.tool;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 智能体工具目录 - Think-Do-Reflect系统的工具集合
 * 
 * 类似于现有的OmniToolCatalog，这个类负责管理Think-Do-Reflect系统
 * 中所有智能体工具的集合，为SupervisorAgent提供完整的工具访问能力。
 */
@Component
public class AgentToolCatalog {

    private final AgentToolbox agentToolbox;
    // 可以添加其他工具，如WebSearchTool、PythonTool等

    public AgentToolCatalog(AgentToolbox agentToolbox) {
        this.agentToolbox = agentToolbox;
    }

    /**
     * 获取所有可用的工具
     * @return 工具对象列表
     */
    public List<Object> getTools() {
        return List.of(agentToolbox);
    }
}
