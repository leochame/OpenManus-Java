package com.openmanus.java.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.openai.internal.chat.ToolCall;
import java.util.List;

/**
 * Represents a chat message, corresponding to app/schema.py's Message.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {

    @JsonProperty("role")
    private Role role;

    @JsonProperty("content")
    private String content;

    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    @JsonProperty("name")
    private String name;

    @JsonProperty("tool_call_id")
    private String toolCallId;

    @JsonProperty("base64_image")
    private String base64Image;

    public Message(Role role, String content, List<ToolCall> toolCalls, String name, String toolCallId, String base64Image) {
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.name = name;
        this.toolCallId = toolCallId;
        this.base64Image = base64Image;
    }

    // Getters and Setters

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getBase64Image() {
        return base64Image;
    }

    public void setBase64Image(String base64Image) {
        this.base64Image = base64Image;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Message userMessage(String content) {
        return new Builder().role(Role.USER).content(content).build();
    }

    public static Message userMessage(String content, String base64Image) {
        return new Builder().role(Role.USER).content(content).base64Image(base64Image).build();
    }

    public static Message systemMessage(String content) {
        return new Builder().role(Role.SYSTEM).content(content).build();
    }

    public static Message assistantMessage(String content) {
        return new Builder().role(Role.ASSISTANT).content(content).build();
    }

    public static Message toolMessage(String content, String name, String toolCallId) {
        return new Builder().role(Role.TOOL).content(content).name(name).toolCallId(toolCallId).build();
    }

    public static class Builder {
        private Role role;
        private String content;
        private List<ToolCall> toolCalls;
        private String name;
        private String toolCallId;
        private String base64Image;

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        public Builder base64Image(String base64Image) {
            this.base64Image = base64Image;
            return this;
        }

        public Message build() {
            return new Message(role, content, toolCalls, name, toolCallId, base64Image);
        }
    }
}