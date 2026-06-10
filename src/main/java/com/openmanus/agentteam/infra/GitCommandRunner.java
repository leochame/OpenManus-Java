package com.openmanus.agentteam.infra;

import java.nio.file.Path;
import java.util.List;

interface GitCommandRunner {

    GitCommandResult run(Path workingDirectory, List<String> command);
}
