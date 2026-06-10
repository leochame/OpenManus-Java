package com.openmanus.agentteam.application;

import com.openmanus.aiframework.runtime.model.AiAgentParameterSchema;
import com.openmanus.aiframework.tool.AiRegisteredTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TeamMasterToolPolicy Tests")
class TeamMasterToolPolicyTest {

    @Test
    @DisplayName("should keep only coordinator support tools")
    void shouldKeepOnlyCoordinatorSupportTools() {
        TeamMasterToolPolicy policy = new TeamMasterToolPolicy();

        List<AiRegisteredTool> selected = policy.selectTools(List.of(
                tool("search_web"),
                tool("browser_fetch_web"),
                tool("runShellCommand"),
                tool("executePython")
        ));

        assertThat(selected)
                .extracting(AiRegisteredTool::name)
                .containsExactly("search_web", "browser_fetch_web", "runShellCommand");
    }

    private AiRegisteredTool tool(String name) {
        return new AiRegisteredTool(
                name,
                name,
                AiAgentParameterSchema.singleStringParameter("input", "input"),
                (request, memoryId) -> "{}"
        );
    }
}
