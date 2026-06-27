package com.ziprun.ai;

import com.ziprun.agent.Agent;
import com.ziprun.routing.RoutingContext;
import com.ziprun.routing.RoutingRecommendation;
import com.ziprun.routing.RoutingStrategy;
import com.ziprun.routing.RuleBasedRoutingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * AI implementation of the routing contract: builds the situation-appropriate prompt, calls
 * the LLM, parses + validates the response, and either uses it or falls back gracefully to
 * the rule-based strategy.
 *
 * <p><b>Resilience (T-3 / ADR-3).</b> Every failure mode collapses to the same safe
 * outcome — a rule-based suggestion, never a silent drop:
 * <ul>
 *   <li>timeout / quota / HTTP error from the gateway → fallback;</li>
 *   <li>unparseable or malformed JSON → fallback;</li>
 *   <li>hallucinated agent id not in the live roster → fallback;</li>
 * </ul>
 * Confidence is clamped to [0,1]. Failures are differentiated by intent: expected LLM
 * outages / bad output ({@link AiRoutingException}, {@code RestClientException}) log at WARN
 * (routine), while unexpected bugs/misconfig log at ERROR with a stack trace so a broken AI
 * path is never masked as "AI unavailable" — both still fall back, so a suggestion is never
 * dropped.
 *
 * <p>Lives in {@code ai} and depends on {@code routing} (one direction); registered under
 * the name {@code "ai"} so the resolver can select it via config at runtime.
 */
@Component
@Slf4j
public class AiRoutingStrategy implements RoutingStrategy {

    public static final String NAME = "ai";

    private final LlmClient llmClient;
    private final RoutingPromptFactory promptFactory;
    private final AiResponseParser parser;
    private final RuleBasedRoutingStrategy ruleBasedFallback;

    public AiRoutingStrategy(LlmClient llmClient, RoutingPromptFactory promptFactory,
                             AiResponseParser parser, RuleBasedRoutingStrategy ruleBasedFallback) {
        this.llmClient = llmClient;
        this.promptFactory = promptFactory;
        this.parser = parser;
        this.ruleBasedFallback = ruleBasedFallback;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<RoutingRecommendation> recommend(RoutingContext context) {
        String orderId = context.order().getId();
        try {
            String prompt = context.isRecovery()
                    ? promptFactory.replanPrompt(context)
                    : promptFactory.initialPrompt(context);

            String raw = llmClient.callLLM(prompt);
            AiRecommendation parsed = parser.parse(raw);
            Agent agent = validateAgent(parsed.agentId(), context.availableAgents());
            double confidence = clamp(parsed.confidence());

            log.info("AI recommended {} (confidence {}) for order {} [{}]",
                    agent.getId(), confidence, orderId, context.isRecovery() ? "re-plan" : "initial");
            return List.of(new RoutingRecommendation(agent, confidence, parsed.reasoning()));
        } catch (AiRoutingException | RestClientException e) {
            // Expected: LLM down/timeout/quota, or bad/hallucinated output. Routine fallback.
            log.warn("AI routing unavailable for order {} ({}: {}). Falling back to rule-based.",
                    orderId, e.getClass().getSimpleName(), e.getMessage());
            return fallback(context);
        } catch (Exception e) {
            // Unexpected: a bug or misconfiguration (e.g. unknown provider). Still fall back so
            // the suggestion is never dropped, but log LOUD so the defect isn't masked as "AI down".
            log.error("Unexpected AI routing failure for order {} — investigate (still falling back)",
                    orderId, e);
            return fallback(context);
        }
    }

    /** Reject hallucinated ids: the recommended agent must be in the live available roster. */
    private Agent validateAgent(String agentId, List<Agent> available) {
        return available.stream()
                .filter(a -> a.getId().equals(agentId))
                .findFirst()
                .orElseThrow(() -> new AiRoutingException(
                        "LLM recommended agent '%s' which is not in the available roster".formatted(agentId)));
    }

    /** Rule-based recommendations, with reasoning prefixed so the fallback is visible in the UI. */
    private List<RoutingRecommendation> fallback(RoutingContext context) {
        return ruleBasedFallback.recommend(context).stream()
                .map(r -> new RoutingRecommendation(r.agent(), r.confidence(),
                        "[AI unavailable — rule-based fallback] " + r.reasoning()))
                .toList();
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
