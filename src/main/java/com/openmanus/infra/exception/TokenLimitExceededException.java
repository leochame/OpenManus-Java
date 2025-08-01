package com.openmanus.infra.exception;

/**
 * Exception raised when the token limit is exceeded.
 * Corresponds to TokenLimitExceeded in the Python version.
 */
public class TokenLimitExceededException extends OpenManusException {
    
    public TokenLimitExceededException(String message) {
        super(message);
    }
    
    public TokenLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}