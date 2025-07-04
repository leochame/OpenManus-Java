package com.openmanus.java.flow;

import lombok.Data;

@Data
public class PlanStep {
    private String description;
    private String type;
    private String status;
}