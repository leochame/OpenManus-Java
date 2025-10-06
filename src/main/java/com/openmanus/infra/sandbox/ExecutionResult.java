package com.openmanus.infra.sandbox;

/**
 * 命令执行结果 - 使用 Record 简化不可变数据对象
 * 
 * 封装沙箱命令执行的输出信息
 */
public record ExecutionResult(String stdout, String stderr, int exitCode) {
    
    /**
     * 规范化构造器，确保非空
     */
    public ExecutionResult {
        stdout = stdout != null ? stdout : "";
        stderr = stderr != null ? stderr : "";
    }
    
    /**
     * 保持向后兼容的 getter 方法
     */
    public String getStdout() {
        return stdout;
    }
    
    public String getStderr() {
        return stderr;
    }
    
    public int getExitCode() {
        return exitCode;
    }
    
    /**
     * 判断执行是否成功
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }
    
    /**
     * 获取合并的输出（stdout + stderr）
     */
    public String getCombinedOutput() {
        StringBuilder sb = new StringBuilder();
        if (!stdout.isEmpty()) {
            sb.append(stdout);
        }
        if (!stderr.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("STDERR: ").append(stderr);
        }
        return sb.toString();
    }
}
