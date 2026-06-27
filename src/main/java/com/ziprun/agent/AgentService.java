package com.ziprun.agent;

import com.ziprun.agent.dto.AgentResponse;
import com.ziprun.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Agent roster + availability changes.
 *
 * <p>Updating status to {@code OFFLINE} is the event that triggers the agentic
 * re-planning loop (T-4). For now the method just persists the change; T-4 will publish
 * a domain event here so re-planning happens off the request path.
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;

    @Transactional(readOnly = true)
    public List<AgentResponse> list() {
        return agentRepository.findAll().stream().map(AgentResponse::from).toList();
    }

    @Transactional
    public AgentResponse updateStatus(String id, AgentStatus newStatus) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Agent", id));

        agent.changeStatus(newStatus);

        // T-4 seam: when newStatus == OFFLINE, publish an AgentWentOfflineEvent here so an
        // @Async listener re-plans the agent's orders without blocking this response.

        return AgentResponse.from(agent);
    }
}
