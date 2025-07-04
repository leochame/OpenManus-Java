package com.openmanus.java.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Message role options, corresponding to app/schema.py's Role.
 */
public enum Role {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}