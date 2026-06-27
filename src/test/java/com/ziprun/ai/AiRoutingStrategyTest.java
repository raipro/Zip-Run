package com.ziprun.ai;

import com.ziprun.agent.Agent;
import com.ziprun.agent.AgentStatus;
import com.ziprun.order.Order;
import com.ziprun.routing.RoutingContext;
import com.ziprun.routing.RoutingRecommendation;
import com.ziprun.routing.RuleBasedRoutingStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AiRoutingStrategyTest {

    private final RoutingPromptFactory promptFactory = new RoutingPromptFactory();
    private final AiResponseParser parser = new AiResponseParser();
    private final RuleBasedRoutingStrategy ruleBased = new RuleBasedRoutingStrategy();

    private Agent agent(String id, int load) {
        return new Agent(id, "Name-" + id, load, AgentStatus.AVAILABLE);
    }

    private RoutingContext context() {
        Order order = new Order("ORD-1", "test", agent("AGT-OWNER", 1));
        return RoutingContext.initial(order, List.of(agent("AGT-002", 0), agent("AGT-004", 2)));
    }

    private AiRoutingStrategy strategyWith(LlmClient llm) {
        return new AiRoutingStrategy(llm, promptFactory, parser, ruleBased);
    }

    @Test
    void name_isAi() {
        assertThat(strategyWith(p -> "").name()).isEqualTo("ai");
    }

    @Test
    void happyPath_usesValidatedAiRecommendation() {
        LlmClient llm = prompt -> "{\"agentId\":\"AGT-004\",\"confidence\":0.77,\"reasoning\":\"AI says so\"}";

        List<RoutingRecommendation> recs = strategyWith(llm).recommend(context());

        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).agent().getId()).isEqualTo("AGT-004");
        assertThat(recs.get(0).confidence()).isEqualTo(0.77);
        assertThat(recs.get(0).reasoning()).isEqualTo("AI says so");
    }

    @Test
    void confidenceIsClamped() {
        LlmClient llm = prompt -> "{\"agentId\":\"AGT-002\",\"confidence\":1.9,\"reasoning\":\"x\"}";
        assertThat(strategyWith(llm).recommend(context()).get(0).confidence()).isEqualTo(1.0);
    }

    @Test
    void hallucinatedAgentId_fallsBackToRuleBased() {
        LlmClient llm = prompt -> "{\"agentId\":\"AGT-999\",\"confidence\":0.9,\"reasoning\":\"ghost\"}";

        List<RoutingRecommendation> recs = strategyWith(llm).recommend(context());

        // Rule-based picks the least-loaded available agent (AGT-002), reasoning flags fallback.
        assertThat(recs.get(0).agent().getId()).isEqualTo("AGT-002");
        assertThat(recs.get(0).reasoning()).startsWith("[AI unavailable — rule-based fallback]");
    }

    @Test
    void gatewayError_fallsBackToRuleBased() {
        LlmClient llm = prompt -> { throw new RuntimeException("timeout"); };

        List<RoutingRecommendation> recs = strategyWith(llm).recommend(context());

        assertThat(recs.get(0).agent().getId()).isEqualTo("AGT-002");
        assertThat(recs.get(0).reasoning()).startsWith("[AI unavailable");
    }

    @Test
    void unparseableResponse_fallsBackToRuleBased() {
        LlmClient llm = prompt -> "the best agent is probably AGT-002 I think";

        List<RoutingRecommendation> recs = strategyWith(llm).recommend(context());

        assertThat(recs.get(0).reasoning()).startsWith("[AI unavailable");
    }

    @Test
    void usesReplanPrompt_whenContextIsRecovery() {
        AtomicReference<String> captured = new AtomicReference<>();
        LlmClient llm = prompt -> {
            captured.set(prompt);
            return "{\"agentId\":\"AGT-002\",\"confidence\":0.6,\"reasoning\":\"recovery pick\"}";
        };
        RoutingContext recovery = RoutingContext.recovery(
                new Order("ORD-1", "test", agent("AGT-OWNER", 1)),
                List.of(agent("AGT-002", 0)),
                agent("AGT-001", 3), 3);

        strategyWith(llm).recommend(recovery);

        assertThat(captured.get()).containsIgnoringCase("OFFLINE");
        assertThat(captured.get()).contains("AGT-001");
    }
}
