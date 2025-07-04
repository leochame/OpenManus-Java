package com.openmanus.java.flow;

import lombok.Data;

import java.util.List;

@Data
public class Plan {
    private String id;
    private List<PlanStep> steps;
}