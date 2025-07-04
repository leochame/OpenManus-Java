package com.openmanus.java.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a function to be called, corresponding to app/schema.py's Function.
 */
public record Function(
    @JsonProperty("name") String name,
    @JsonProperty("arguments") String arguments
) {}