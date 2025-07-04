package com.openmanus.java.exception;

/**
 * Base exception for all OpenManus errors.
 * Corresponds to OpenManusError in the Python version.
 */
public class OpenManusException extends RuntimeException {
    
    public OpenManusException(String message) {
        super(message);
    }
    
    public OpenManusException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public OpenManusException(Throwable cause) {
        super(cause);
    }
}