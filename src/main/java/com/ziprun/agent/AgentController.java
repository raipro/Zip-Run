package com.ziprun.agent;

import com.ziprun.agent.dto.AgentResponse;
import com.ziprun.agent.dto.UpdateAgentStatusRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent roster + availability (T-1). {@code PATCH /agents/{id}/status} is what fires the
 * agentic loop (T-4); it returns immediately and never blocks on re-planning.
 */
@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /** GET /agents — roster for the ops UI (status + current load). */
    @GetMapping
    public List<AgentResponse> list() {
        return agentService.list();
    }

    /** PATCH /agents/{id}/status — update availability; OFFLINE triggers re-planning. */
    @PatchMapping("/{id}/status")
    public AgentResponse updateStatus(@PathVariable String id,
                                      @Valid @RequestBody UpdateAgentStatusRequest request) {
        return agentService.updateStatus(id, request.getStatus());
    }
}
