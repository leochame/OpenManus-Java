package com.openmanus.java.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Tool choice options, corresponding to app/schema.py's ToolChoice.
 */
public enum ToolChoice {
    NONE("none"),
    AUTO("auto"),
    REQUIRED("required");

    private final String value;

    ToolChoice(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}