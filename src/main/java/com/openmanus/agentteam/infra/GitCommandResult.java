package com.openmanus.agentteam.infra;

record GitCommandResult(int exitCode, String stdout, String stderr) {

    boolean isSuccess() {
        return exitCode == 0;
    }
}
