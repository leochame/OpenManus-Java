package com.openmanus.infra.exception;

public class ToolErrorException extends Exception {
    public ToolErrorException(String message) {
        super(message);
    }
    
    public ToolErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}