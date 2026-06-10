package com.openmanus.agentteam.application;

import java.util.Map;

final class PromptTemplateRenderer {

    private PromptTemplateRenderer() {
    }

    static String render(String template, Map<String, String> variables) {
        String rendered = template == null ? "" : template;
        if (variables == null || variables.isEmpty()) {
            return rendered;
        }
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue();
            rendered = rendered.replace("{{" + key + "}}", value);
        }
        return rendered;
    }
}
