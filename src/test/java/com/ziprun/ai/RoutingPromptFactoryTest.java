package com.ziprun.ai;

import com.ziprun.agent.Agent;
import com.ziprun.agent.AgentStatus;
import com.ziprun.order.Order;
import com.ziprun.routing.RoutingContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingPromptFactoryTest {

    private final RoutingPromptFactory factory = new RoutingPromptFactory();

    private Agent agent(String id, int load) {
        return new Agent(id, "Name-" + id, load, AgentStatus.AVAILABLE);
    }

    private Order order() {
        return new Order("ORD-7", "Pharma — Whitefield to Marathahalli", agent("AGT-OWNER", 1));
    }

    @Test
    void initialPrompt_containsOrderAgentsAndJsonContract_butNoRecoveryFraming() {
        String prompt = factory.initialPrompt(
                RoutingContext.initial(order(), List.of(agent("AGT-002", 0), agent("AGT-004", 1))));

        assertThat(prompt).contains("ORD-7", "Pharma", "AGT-002", "AGT-004", "agentId", "confidence");
        assertThat(prompt).doesNotContainIgnoringCase("OFFLINE");
        assertThat(prompt).doesNotContainIgnoringCase("stranded");
    }

    @Test
    void replanPrompt_isRecoveryFramedWithOfflineAgentAndStrandedCount() {
        Agent offline = agent("AGT-001", 3);
        String prompt = factory.replanPrompt(
                RoutingContext.recovery(order(), List.of(agent("AGT-002", 0)), offline, 3));

        assertThat(prompt).containsIgnoringCase("OFFLINE");
        assertThat(prompt).containsIgnoringCase("recovery");
        assertThat(prompt).contains("AGT-001");   // the failed agent
        assertThat(prompt).contains("3");          // stranded count
        assertThat(prompt).contains("AGT-002");    // candidate
    }

    @Test
    void initialAndReplanPromptsAreGenuinelyDifferent() {
        RoutingContext base = RoutingContext.initial(order(), List.of(agent("AGT-002", 0)));
        RoutingContext recovery = RoutingContext.recovery(order(), List.of(agent("AGT-002", 0)), agent("AGT-001", 3), 2);

        assertThat(factory.initialPrompt(base)).isNotEqualTo(factory.replanPrompt(recovery));
    }
}
