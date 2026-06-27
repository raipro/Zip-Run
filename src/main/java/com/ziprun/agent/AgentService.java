package com.ziprun.agent;

import com.ziprun.agent.dto.AgentResponse;
import com.ziprun.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<AgentResponse> list() {
        return agentRepository.findAll().stream().map(AgentResponse::from).toList();
    }

    @Transactional
    public AgentResponse updateStatus(String id, AgentStatus newStatus) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Agent", id));

        AgentStatus previous = agent.getStatus();
        agent.changeStatus(newStatus);

        // Fire the agentic loop only on the transition INTO offline. The event is handled
        // after this transaction commits, on a separate thread, so this call returns at once.
        if (newStatus == AgentStatus.OFFLINE && previous != AgentStatus.OFFLINE) {
            eventPublisher.publishEvent(new AgentWentOfflineEvent(id));
        }

        return AgentResponse.from(agent);
    }
}
