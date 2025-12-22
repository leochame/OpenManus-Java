package com.openmanus.agent.base;

import dev.langchain4j.data.message.SystemMessage;

import java.util.Objects;

/**
 * A concrete implementation of {@link AbstractAgentExecutor} that acts as a supervisor,
 * delegating tasks to other agents registered as tools.
 * <p>
 * Its primary role is to orchestrate sub-agents to accomplish a broader goal.
 */
public class AgentHandoff extends AbstractAgentExecutor<AgentHandoff.Builder> {

    private AgentHandoff(Builder builder) {
        super(builder);
    }

    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {

        public <B extends AbstractAgent.Builder<B>> Builder agent(AbstractAgent<B> agent) {
            // Add a sub-agent as a tool for the supervisor to use.
            tool(Objects.requireNonNull(agent, "agent cannot be null").asTool());
            return result();
        }

        public Builder systemMessage(String systemMessageTemplate) {
            systemMessage(SystemMessage.from(systemMessageTemplate));
            return result();
        }

        public AgentHandoff build() {
            return new AgentHandoff(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}