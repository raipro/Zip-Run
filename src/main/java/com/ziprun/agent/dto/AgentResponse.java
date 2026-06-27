package com.ziprun.agent.dto;

import com.ziprun.agent.Agent;
import com.ziprun.agent.AgentStatus;
import lombok.Getter;

/**
 * API view of an agent for the ops roster — id, name, current load, and status.
 */
@Getter
public class AgentResponse {

    private final String id;
    private final String name;
    private final int activeOrderCount;
    private final AgentStatus status;

    public AgentResponse(String id, String name, int activeOrderCount, AgentStatus status) {
        this.id = id;
        this.name = name;
        this.activeOrderCount = activeOrderCount;
        this.status = status;
    }

    public static AgentResponse from(Agent agent) {
        return new AgentResponse(agent.getId(), agent.getName(),
                agent.getActiveOrderCount(), agent.getStatus());
    }
}
