package com.ziprun.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTest {

    private Agent agent(int load, AgentStatus status) {
        return new Agent("AGT-1", "Priya", load, status);
    }

    @Test
    void isAvailable_onlyWhenStatusAvailable() {
        assertThat(agent(0, AgentStatus.AVAILABLE).isAvailable()).isTrue();
        assertThat(agent(0, AgentStatus.BUSY).isAvailable()).isFalse();
        assertThat(agent(0, AgentStatus.OFFLINE).isAvailable()).isFalse();
    }

    @Test
    void incrementLoad_raisesCount() {
        Agent agent = agent(1, AgentStatus.BUSY);
        agent.incrementLoad();
        assertThat(agent.getActiveOrderCount()).isEqualTo(2);
    }

    @Test
    void decrementLoad_lowersCount() {
        Agent agent = agent(2, AgentStatus.BUSY);
        agent.decrementLoad();
        assertThat(agent.getActiveOrderCount()).isEqualTo(1);
    }

    @Test
    void decrementLoad_floorsAtZero() {
        Agent agent = agent(0, AgentStatus.AVAILABLE);
        agent.decrementLoad();
        assertThat(agent.getActiveOrderCount()).isZero();
    }

    @Test
    void changeStatus_updatesAvailability() {
        Agent agent = agent(0, AgentStatus.AVAILABLE);
        agent.changeStatus(AgentStatus.OFFLINE);
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.OFFLINE);
    }
}
