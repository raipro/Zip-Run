package com.ziprun.agent.dto;

import com.ziprun.agent.AgentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Update an agent's availability. Setting {@code OFFLINE} is what fires the agentic
 * re-planning loop (T-4).
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateAgentStatusRequest {

    @NotNull
    private AgentStatus status;
}
