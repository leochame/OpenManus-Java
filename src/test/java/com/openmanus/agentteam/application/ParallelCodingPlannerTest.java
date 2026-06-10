package com.openmanus.agentteam.application;

import com.openmanus.agentteam.domain.model.ParallelCodingPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ParallelCodingPlanner Tests")
class ParallelCodingPlannerTest {

    private final ParallelCodingPlanner planner = new ParallelCodingPlanner();

    @Test
    @DisplayName("should create parallel coding plan from explicit bullet subtasks")
    void shouldCreateParallelCodingPlanFromExplicitBulletSubtasks() {
        ParallelCodingPlan plan = planner.plan("""
                - 后端实现新接口字段
                - 前端展示新字段
                - 补充测试
                """, 5);

        assertThat(plan.parallelizable()).isTrue();
        assertThat(plan.subTasks()).hasSize(3);
        assertThat(plan.subTasks().get(0).taskId()).isEqualTo("code-task-1");
        assertThat(plan.subTasks().get(1).ownedPaths()).contains("frontend/");
        assertThat(plan.subTasks().get(2).ownedPaths()).contains("src/test/java/");
    }

    @Test
    @DisplayName("should reject plan with dependency hints")
    void shouldRejectPlanWithDependencyHints() {
        ParallelCodingPlan plan = planner.plan("""
                - 先完成后端接口
                - 然后基于前面的接口调整前端
                """, 5);

        assertThat(plan.parallelizable()).isFalse();
        assertThat(plan.reason()).contains("dependency");
    }
}
