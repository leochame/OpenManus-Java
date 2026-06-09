package com.openmanus.agentteam.application;

import com.openmanus.agentteam.domain.model.CodeSubTask;
import com.openmanus.agentteam.domain.model.ParallelCodingPlan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal planner for deciding whether a user request can be executed as parallel coding subtasks.
 */
public class ParallelCodingPlanner {

    private static final Pattern BULLET_PATTERN = Pattern.compile(
            "^(?:[-*]|\\d+[.)]|[a-zA-Z][.)]|[一二三四五六七八九十]+[、.])\\s*(.+)$"
    );

    private static final List<String> DEPENDENCY_HINTS = List.of(
            "然后", "之后", "完成后", "基于前", "依赖", "after", "then", "depends on", "based on"
    );

    public ParallelCodingPlan plan(String userInput, int maxSubTasks) {
        if (userInput == null || userInput.isBlank()) {
            return new ParallelCodingPlan(false, "Task is empty and cannot be planned", List.of());
        }
        List<CodeSubTask> subTasks = extractSubTasks(userInput, maxSubTasks);
        if (subTasks.size() < 2) {
            return new ParallelCodingPlan(false, "Fewer than two explicit coding subtasks were found", subTasks);
        }
        if (containsDependencyHints(subTasks)) {
            return new ParallelCodingPlan(false, "Detected dependency hints between coding subtasks", subTasks);
        }
        return new ParallelCodingPlan(true, "Explicit independent coding subtasks detected", subTasks);
    }

    private List<CodeSubTask> extractSubTasks(String userInput, int maxSubTasks) {
        String[] lines = userInput.split("\\R");
        Set<String> normalizedGoals = new LinkedHashSet<>();
        List<CodeSubTask> subTasks = new ArrayList<>();
        int limit = Math.max(2, maxSubTasks);
        for (String line : lines) {
            String content = extractBulletContent(line);
            if (content == null || content.isBlank()) {
                continue;
            }
            String normalized = content.trim();
            if (!normalizedGoals.add(normalized)) {
                continue;
            }
            int index = subTasks.size() + 1;
            subTasks.add(new CodeSubTask(
                    "code-task-" + index,
                    buildTitle(index, normalized),
                    normalized,
                    inferOwnedPaths(normalized),
                    List.of(),
                    inferVerificationCommands(normalized),
                    List.of(),
                    inferConflictRisk(normalized)
            ));
            if (subTasks.size() >= limit) {
                break;
            }
        }
        return subTasks;
    }

    private String extractBulletContent(String line) {
        if (line == null) {
            return null;
        }
        Matcher matcher = BULLET_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1);
    }

    private boolean containsDependencyHints(List<CodeSubTask> subTasks) {
        for (CodeSubTask subTask : subTasks) {
            String lower = subTask.goal().toLowerCase();
            for (String hint : DEPENDENCY_HINTS) {
                if (lower.contains(hint.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String buildTitle(int index, String goal) {
        String compact = goal.length() > 30 ? goal.substring(0, 30) : goal;
        return "CodeSubTask-" + index + ": " + compact;
    }

    private List<String> inferOwnedPaths(String goal) {
        String lower = goal.toLowerCase();
        List<String> ownedPaths = new ArrayList<>();
        if (lower.contains("frontend") || goal.contains("前端") || lower.contains("ui")) {
            ownedPaths.add("frontend/");
        }
        if (lower.contains("backend") || goal.contains("后端") || lower.contains("api")
                || lower.contains("service")) {
            ownedPaths.add("src/main/java/");
        }
        if (lower.contains("test") || goal.contains("测试")) {
            ownedPaths.add("src/test/java/");
        }
        return ownedPaths;
    }

    private List<String> inferVerificationCommands(String goal) {
        String lower = goal.toLowerCase();
        if (lower.contains("frontend") || goal.contains("前端") || lower.contains("ui")) {
            return List.of("npm test -- --runInBand");
        }
        if (lower.contains("test") || goal.contains("测试")) {
            return List.of("./scripts/mvnw-local.sh -q -DskipITs test");
        }
        return List.of("./scripts/mvnw-local.sh -q -DskipTests compile");
    }

    private String inferConflictRisk(String goal) {
        String lower = goal.toLowerCase();
        if (lower.contains("same file") || goal.contains("同一文件")) {
            return "high";
        }
        return "low";
    }
}
