package com.openmanus.agentteam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.agentteam.domain.model.DecompositionPlan;
import com.openmanus.agentteam.domain.model.SubTaskPlan;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.model.AiChatMessage;
import com.openmanus.aiframework.runtime.model.AiChatResponse;
import com.openmanus.aiframework.runtime.model.AiChatRequest;
import com.openmanus.aiframework.runtime.model.AiToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * First-version decomposition service.
 *
 * <p>V1 now prefers LLM-based structured decomposition and keeps a conservative
 * rule-based fallback for robustness.</p>
 */
public class TaskDecompositionService {

    private static final Logger log = LoggerFactory.getLogger(TaskDecompositionService.class);
    private static final String STRUCTURED_OUTPUT_TOOL_NAME = "structured_output";
    private static final double DECOMPOSITION_TEMPERATURE = 0.1D;
    private static final int DECOMPOSITION_MAX_OUTPUT_TOKENS = 1200;
    private static final int DECOMPOSITION_TIMEOUT_SECONDS = 30;

    private static final Pattern BULLET_PATTERN = Pattern.compile(
            "^(?:[-*]|\\d+[.)]|[a-zA-Z][.)]|[一二三四五六七八九十]+[、.])\\s*(.+)$"
    );

    private static final List<String> DEPENDENCY_HINTS = List.of(
            "然后", "之后", "完成后", "基于前", "依赖", "先", "after", "then", "based on", "depends on"
    );

    private final AiChatModel aiChatModel;
    private final ObjectMapper objectMapper;
    private final AgentTeamPromptProvider promptProvider;

    public TaskDecompositionService(
            AiChatModel aiChatModel,
            ObjectMapper objectMapper,
            AgentTeamPromptProvider promptProvider
    ) {
        this.aiChatModel = aiChatModel;
        this.objectMapper = objectMapper;
        this.promptProvider = promptProvider;
    }

    public DecompositionPlan decompose(String userInput, int maxSubTasks) {
        if (userInput == null || userInput.isBlank()) {
            return new DecompositionPlan(false, "Task is empty and cannot be decomposed", List.of());
        }

        DecompositionPlan llmPlan = tryLlmDecompose(userInput, maxSubTasks);
        if (llmPlan != null) {
            return enrichContextSummary(llmPlan, userInput);
        }

        return enrichContextSummary(ruleBasedDecompose(userInput, maxSubTasks), userInput);
    }

    private DecompositionPlan tryLlmDecompose(String userInput, int maxSubTasks) {
        try {
            String prompt = PromptTemplateRenderer.render(
                    promptProvider.taskDecompositionPromptTemplate(),
                    Map.of(
                            "userInput", userInput,
                            "maxSubTasks", String.valueOf(Math.max(2, maxSubTasks))
                    )
            );
            AiChatRequest request = new AiChatRequest(
                    "",
                    List.of(AiChatMessage.user(prompt)),
                    List.of(),
                    DECOMPOSITION_TEMPERATURE,
                    DECOMPOSITION_MAX_OUTPUT_TOKENS,
                    DECOMPOSITION_TIMEOUT_SECONDS,
                    buildResponseFormat(maxSubTasks)
            );
            AiChatResponse response = aiChatModel.chat(request);
            String payload = extractStructuredPayload(response);
            if (payload == null || payload.isBlank()) {
                log.warn("LLM decomposition returned empty payload, falling back to rule-based decomposition");
                return null;
            }

            JsonNode root = objectMapper.readTree(payload);
            return validateLlmPlan(toPlan(root, maxSubTasks), maxSubTasks);
        } catch (Exception exception) {
            log.warn("LLM decomposition failed, falling back to rule-based decomposition: {}", exception.getMessage());
            return null;
        }
    }

    private DecompositionPlan validateLlmPlan(DecompositionPlan plan, int maxSubTasks) {
        List<SubTaskPlan> candidates = normalizeSubTasks(plan.subTasks(), maxSubTasks);
        String reason = normalizeReason(plan.reason(), plan.parallelizable()
                ? "LLM identified independent parallel subtasks"
                : "LLM judged the request unsafe to parallelize");

        if (!plan.parallelizable()) {
            return new DecompositionPlan(false, reason, candidates);
        }
        if (candidates.size() < 2) {
            return new DecompositionPlan(false, "LLM returned fewer than two valid subtasks", candidates);
        }
        if (containsDependencyHints(candidates)) {
            return new DecompositionPlan(false, "LLM subtasks contain obvious dependency hints", candidates);
        }
        return new DecompositionPlan(true, reason, candidates);
    }

    private DecompositionPlan toPlan(JsonNode root, int maxSubTasks) {
        if (root == null || !root.isObject()) {
            return new DecompositionPlan(false, "LLM output is not a JSON object", List.of());
        }
        boolean parallelizable = root.path("parallelizable").asBoolean(false);
        String reason = normalizeReason(root.path("reason").asText(null), "");
        List<SubTaskPlan> subTasks = parseSubTasks(root.path("subTasks"), maxSubTasks);
        return new DecompositionPlan(parallelizable, reason, subTasks);
    }

    private List<SubTaskPlan> parseSubTasks(JsonNode node, int maxSubTasks) {
        if (!(node instanceof ArrayNode arrayNode)) {
            return List.of();
        }
        List<SubTaskPlan> results = new ArrayList<>();
        int limit = Math.max(2, maxSubTasks);
        for (JsonNode item : arrayNode) {
            if (!item.isObject()) {
                continue;
            }
            String description = normalizeReason(item.path("description").asText(null), "");
            if (description.isBlank()) {
                continue;
            }
            String title = normalizeReason(item.path("title").asText(null), "");
            if (title.isBlank()) {
                title = buildTitle(results.size() + 1, description);
            }
            String contextSummary = normalizeReason(item.path("contextSummary").asText(null), "");
            results.add(new SubTaskPlan(title, description, contextSummary));
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }

    private String extractStructuredPayload(AiChatResponse response) {
        if (response == null || response.message() == null) {
            return null;
        }
        for (AiToolCall toolCall : response.message().toolCalls()) {
            if (toolCall == null || toolCall.name() == null || toolCall.name().isBlank()) {
                continue;
            }
            if (STRUCTURED_OUTPUT_TOOL_NAME.equals(toolCall.name()) || response.message().content().isBlank()) {
                return toolCall.arguments();
            }
        }
        return unwrapCodeFence(response.message().content());
    }

    private String unwrapCodeFence(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineBreak = trimmed.indexOf('\n');
        if (firstLineBreak < 0) {
            return trimmed;
        }
        int lastFence = trimmed.lastIndexOf("```");
        if (lastFence <= firstLineBreak) {
            return trimmed.substring(firstLineBreak + 1).trim();
        }
        return trimmed.substring(firstLineBreak + 1, lastFence).trim();
    }

    private JsonNode buildResponseFormat(int maxSubTasks) {
        ObjectNode responseFormat = objectMapper.createObjectNode();
        responseFormat.put("type", "json_schema");

        ObjectNode jsonSchema = responseFormat.putObject("jsonSchema");
        jsonSchema.put("name", "task_decomposition_plan");

        ObjectNode schema = jsonSchema.putObject("schema");
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        ObjectNode properties = schema.putObject("properties");
        properties.putObject("parallelizable").put("type", "boolean");
        properties.putObject("reason").put("type", "string");

        ObjectNode subTasks = properties.putObject("subTasks");
        subTasks.put("type", "array");
        subTasks.put("maxItems", Math.max(2, maxSubTasks));

        ObjectNode itemSchema = subTasks.putObject("items");
        itemSchema.put("type", "object");
        itemSchema.put("additionalProperties", false);

        ObjectNode itemProperties = itemSchema.putObject("properties");
        itemProperties.putObject("title").put("type", "string");
        itemProperties.putObject("description").put("type", "string");
        itemProperties.putObject("contextSummary").put("type", "string");

        ArrayNode itemRequired = itemSchema.putArray("required");
        itemRequired.add("title");
        itemRequired.add("description");

        ArrayNode rootRequired = schema.putArray("required");
        rootRequired.add("parallelizable");
        rootRequired.add("reason");
        rootRequired.add("subTasks");
        return responseFormat;
    }

    private DecompositionPlan ruleBasedDecompose(String userInput, int maxSubTasks) {
        List<SubTaskPlan> candidates = extractCandidates(userInput, maxSubTasks);
        if (candidates.size() < 2) {
            return new DecompositionPlan(false, "Rule fallback found fewer than two parallel subtasks", candidates);
        }
        if (containsDependencyHints(candidates)) {
            return new DecompositionPlan(false, "Rule fallback detected obvious dependency between subtasks", candidates);
        }
        return new DecompositionPlan(true, "Rule fallback recognized explicit independent subtasks", candidates);
    }

    private List<SubTaskPlan> extractCandidates(String userInput, int maxSubTasks) {
        String[] lines = userInput.split("\\R");
        Set<String> normalizedDescriptions = new LinkedHashSet<>();
        List<SubTaskPlan> results = new ArrayList<>();
        int limit = Math.max(2, maxSubTasks);

        for (String line : lines) {
            String candidate = extractBulletContent(line);
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String normalized = candidate.trim();
            if (!normalizedDescriptions.add(normalized)) {
                continue;
            }
            results.add(new SubTaskPlan(buildTitle(results.size() + 1, normalized), normalized, ""));
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }

    private List<SubTaskPlan> normalizeSubTasks(List<SubTaskPlan> subTasks, int maxSubTasks) {
        if (subTasks == null || subTasks.isEmpty()) {
            return List.of();
        }
        Set<String> normalizedDescriptions = new LinkedHashSet<>();
        List<SubTaskPlan> results = new ArrayList<>();
        int limit = Math.max(2, maxSubTasks);

        for (SubTaskPlan subTask : subTasks) {
            if (subTask == null) {
                continue;
            }
            String description = normalizeReason(subTask.description(), "");
            if (description.isBlank()) {
                continue;
            }
            if (!normalizedDescriptions.add(description)) {
                continue;
            }
            String title = normalizeReason(subTask.title(), "");
            if (title.isBlank()) {
                title = buildTitle(results.size() + 1, description);
            }
            results.add(new SubTaskPlan(title, description, normalizeReason(subTask.contextSummary(), "")));
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }

    private String extractBulletContent(String rawLine) {
        if (rawLine == null) {
            return null;
        }
        String line = rawLine.trim();
        Matcher matcher = BULLET_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1);
    }

    private boolean containsDependencyHints(List<SubTaskPlan> subTasks) {
        for (SubTaskPlan subTask : subTasks) {
            String lower = subTask.description().toLowerCase();
            for (String hint : DEPENDENCY_HINTS) {
                if (lower.contains(hint.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String buildTitle(int index, String description) {
        String compact = description.length() > 24 ? description.substring(0, 24) : description;
        return "SubTask-" + index + ": " + compact;
    }

    private DecompositionPlan enrichContextSummary(DecompositionPlan plan, String userInput) {
        if (plan == null) {
            return null;
        }
        List<SubTaskPlan> enriched = new ArrayList<>();
        for (SubTaskPlan subTask : plan.subTasks()) {
            if (subTask == null) {
                continue;
            }
            String contextSummary = normalizeReason(subTask.contextSummary(), fallbackContextSummary(userInput));
            enriched.add(new SubTaskPlan(subTask.title(), subTask.description(), contextSummary));
        }
        return new DecompositionPlan(plan.parallelizable(), plan.reason(), enriched);
    }

    private String fallbackContextSummary(String userInput) {
        String request = normalizeReason(userInput, "");
        return """
                Parent request:
                %s

                Team instruction:
                Complete only this assigned subtask. Return concise evidence and result for Team Master aggregation.
                """.formatted(request).trim();
    }

    private String normalizeReason(String reason, String fallback) {
        if (reason == null || reason.isBlank()) {
            return fallback;
        }
        return reason.trim();
    }
}
