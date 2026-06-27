package com.ziprun.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRepository extends JpaRepository<Agent, String> {

    /** Roster of agents that can currently take work — the input to the routing engine. */
    List<Agent> findByStatus(AgentStatus status);
}
