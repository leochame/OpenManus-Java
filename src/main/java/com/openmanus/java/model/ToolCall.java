package com.openmanus.java.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a tool/function call in a message, corresponding to app/schema.py's ToolCall.
 */
public record ToolCall(
    @JsonProperty("id") String id,
    @JsonProperty("type") String type,
    @JsonProperty("function") Function function
) {
    public ToolCall(String id, Function function) {
        this(id, "function", function);
    }
}