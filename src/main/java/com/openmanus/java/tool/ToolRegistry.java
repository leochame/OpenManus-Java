package com.openmanus.java.tool;

import java.util.*;
import java.util.stream.Collectors;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.output.structured.Description;

public class ToolRegistry {

    private final Map<String, ToolExecutor> toolExecutors;
    private final List<ToolSpecification> toolSpecifications;
    private final ObjectMapper objectMapper;

    public ToolRegistry(Object... tools) {
        this.toolExecutors = new HashMap<>();
        this.toolSpecifications = new ArrayList<>();
        this.objectMapper = new ObjectMapper();

        for (Object tool : tools) {
            // Skip null tools
            if (tool == null) {
                continue;
            }
            
            // Use ToolSpecifications.toolSpecificationsFrom() for automatic generation
            List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(tool.getClass());
            toolSpecifications.addAll(specs);
            
            // Register tool executors for each method
            processToolObject(tool);
        }
    }

    private void processToolObject(Object toolObject) {
        Class<?> toolClass = toolObject.getClass();
        Method[] methods = toolClass.getDeclaredMethods();

        for (Method method : methods) {
            if (method.isAnnotationPresent(Tool.class)) {
                Tool toolAnnotation = method.getAnnotation(Tool.class);
                String toolName = toolAnnotation.name().isEmpty() ? method.getName() : toolAnnotation.name();

                // Tool specifications are now automatically generated above
                // Just register the executor

                // Create ToolExecutor using lambda
                ToolExecutor executor = (toolExecutionRequest, memoryId) -> {
                    try {
                        Object[] args = extractMethodArgs(method, toolExecutionRequest);
                        Object result = method.invoke(toolObject, args);
                        return result != null ? result.toString() : "";
                    } catch (Exception e) {
                        return "Error executing tool: " + e.getMessage();
                    }
                };

                toolExecutors.put(toolName, executor);
            }
        }
    }

    private String getParameterType(Class<?> paramType) {
        if (paramType == String.class) {
            return "string";
        } else if (paramType == int.class || paramType == Integer.class) {
            return "integer";
        } else if (paramType == boolean.class || paramType == Boolean.class) {
            return "boolean";
        } else if (paramType == double.class || paramType == Double.class || 
                   paramType == float.class || paramType == Float.class) {
            return "number";
        } else {
            return "string"; // Default to string for complex types
        }
    }

    private Object[] extractMethodArgs(Method method, ToolExecutionRequest request) {
        try {
            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];
            
            // Parse arguments from the request
            String argumentsJson = request.arguments();
            JsonNode argumentsNode = objectMapper.readTree(argumentsJson);
            
            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];
                String paramName = param.getName();
                Class<?> paramType = param.getType();
                
                JsonNode valueNode = argumentsNode.get(paramName);
                if (valueNode != null) {
                    args[i] = convertJsonNodeToType(valueNode, paramType);
                } else {
                    // Provide default values for missing parameters
                    args[i] = getDefaultValue(paramType);
                }
            }
            
            return args;
        } catch (Exception e) {
            // Return default values if parsing fails
            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                args[i] = getDefaultValue(parameters[i].getType());
            }
            return args;
        }
    }
    
    private Object convertJsonNodeToType(JsonNode node, Class<?> targetType) {
        if (node == null || node.isNull()) {
            return getDefaultValue(targetType);
        }
        
        if (targetType == String.class) {
            return node.asText();
        } else if (targetType == int.class || targetType == Integer.class) {
            return node.asInt();
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return node.asBoolean();
        } else if (targetType == double.class || targetType == Double.class) {
            return node.asDouble();
        } else if (targetType == float.class || targetType == Float.class) {
            return (float) node.asDouble();
        } else if (List.class.isAssignableFrom(targetType)) {
            // Handle List types
            if (node.isArray()) {
                List<Object> list = new ArrayList<>();
                for (JsonNode element : node) {
                    if (element.isInt()) {
                        list.add(element.asInt());
                    } else if (element.isBoolean()) {
                        list.add(element.asBoolean());
                    } else if (element.isDouble()) {
                        list.add(element.asDouble());
                    } else {
                        list.add(element.asText());
                    }
                }
                return list;
            }
            return new ArrayList<>();
        } else {
            return node.asText(); // Default to string
        }
    }
    
    private Object getDefaultValue(Class<?> type) {
        if (type == String.class) {
            return null; // Allow null for optional string parameters
        } else if (type == int.class || type == Integer.class) {
            return null; // Allow null for optional integer parameters
        } else if (type == boolean.class || type == Boolean.class) {
            return false;
        } else if (type == double.class || type == Double.class) {
            return 0.0;
        } else if (type == float.class || type == Float.class) {
            return 0.0f;
        } else if (List.class.isAssignableFrom(type)) {
            return null; // Allow null for optional list parameters
        } else {
            return null;
        }
    }

    public List<ToolSpecification> getToolSpecifications() {
        return toolSpecifications;
    }

    public List<ToolSpecification> getAllToolSpecifications() {
        return toolSpecifications;
    }

    public ToolExecutor getToolExecutor(String toolName) {
        return toolExecutors.get(toolName);
    }
    
    public int getToolCount() {
        return toolSpecifications.size();
    }
    
    public boolean hasToolByName(String toolName) {
        return toolExecutors.containsKey(toolName);
    }
    
    public Object execute(String toolName, Object args) {
        ToolExecutor executor = toolExecutors.get(toolName);
        if (executor == null) {
            throw new IllegalArgumentException("Tool not found: " + toolName);
        }
        
        try {
            // Convert args to JSON string if it's not already
            String argsJson;
            if (args instanceof String) {
                argsJson = (String) args;
            } else {
                argsJson = objectMapper.writeValueAsString(args);
            }
            
            // Create ToolExecutionRequest
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(toolName)
                .arguments(argsJson)
                .build();
            
            return executor.execute(request, null);
        } catch (Exception e) {
            throw new RuntimeException("Error executing tool: " + toolName, e);
        }
    }
    
    public List<Object> getAllTools() {
        // Return a list of tool names for now
        return new ArrayList<>(toolExecutors.keySet());
    }
    
    public List<ToolSpecification> getToolParams() {
        return toolSpecifications;
    }
}
