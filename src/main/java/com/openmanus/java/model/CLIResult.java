package com.openmanus.java.model;

/**
 * Represents the result of a command line interface (CLI) execution.
 * Corresponds to CLIResult in the Python version.
 */
@Deprecated
public class CLIResult {
    /**
     * Standard output from the command
     */
    private String output;
    
    /**
     * Standard error from the command
     */
    private String error;
    
    /**
     * System message (e.g., "tool has been restarted")
     */
    private String system;
    
    /**
     * Exit code of the command
     */
    private int exitCode;
    
    /**
     * Whether the command execution was successful
     */
    private boolean success;
    
    // Constructors
    public CLIResult() {}
    
    public CLIResult(String output, String error, String system, int exitCode, boolean success) {
        this.output = output;
        this.error = error;
        this.system = system;
        this.exitCode = exitCode;
        this.success = success;
    }
    
    // Getters and Setters
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    
    public String getSystem() { return system; }
    public void setSystem(String system) { this.system = system; }
    
    public int getExitCode() { return exitCode; }
    public void setExitCode(int exitCode) { this.exitCode = exitCode; }
    
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String output;
        private String error;
        private String system;
        private int exitCode;
        private boolean success;
        
        public Builder output(String output) {
            this.output = output;
            return this;
        }
        
        public Builder error(String error) {
            this.error = error;
            return this;
        }
        
        public Builder system(String system) {
            this.system = system;
            return this;
        }
        
        public Builder exitCode(int exitCode) {
            this.exitCode = exitCode;
            return this;
        }
        
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }
        
        public CLIResult build() {
            return new CLIResult(output, error, system, exitCode, success);
        }
    }
    
    /**
     * Create a CLIResult with only output
     */
    public static CLIResult withOutput(String output) {
        return CLIResult.builder()
                .output(output)
                .success(true)
                .exitCode(0)
                .build();
    }
    
    /**
     * Create a CLIResult with only error
     */
    public static CLIResult withError(String error) {
        return CLIResult.builder()
                .error(error)
                .success(false)
                .exitCode(1)
                .build();
    }
    
    /**
     * Create a CLIResult with only system message
     */
    public static CLIResult withSystem(String system) {
        return CLIResult.builder()
                .system(system)
                .success(true)
                .exitCode(0)
                .build();
    }
    
    /**
     * Get a formatted string representation of the result
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        if (system != null && !system.isEmpty()) {
            sb.append("SYSTEM: ").append(system);
        }
        
        if (output != null && !output.isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(output);
        }
        
        if (error != null && !error.isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("ERROR: ").append(error);
        }
        
        if (!success && sb.length() == 0) {
            sb.append("Command failed with exit code: ").append(exitCode);
        }
        
        return sb.toString();
    }
}