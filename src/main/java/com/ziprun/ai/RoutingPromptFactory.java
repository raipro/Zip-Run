package com.ziprun.ai;

import com.ziprun.agent.Agent;
import com.ziprun.order.Order;
import com.ziprun.routing.RoutingContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the two genuinely-different prompts the AI strategy uses (T-3).
 *
 * <ul>
 *   <li><b>Initial</b> — a fresh assignment: "pick the best agent for this order".</li>
 *   <li><b>Re-plan</b> — a <i>situation report</i>: an agent went offline, N orders are
 *       stranded, prior assignments are void, this is a recovery. The model is told what
 *       happened, not handed the same form with a flag flipped.</li>
 * </ul>
 *
 * Both request the same strict JSON output schema so the parser is shared.
 */
@Component
public class RoutingPromptFactory {

    private static final String OUTPUT_CONTRACT = """
            Respond with ONLY a JSON object — no markdown fences, no prose before or after:
            {"agentId":"<one of the agent ids listed above>","confidence":<number between 0 and 1>,\
            "reasoning":"<one or two sentences an ops manager can act on>"}""";

    public String initialPrompt(RoutingContext context) {
        Order order = context.order();
        return """
                You are a dispatch routing assistant for ZipRun, a city delivery fleet.
                A delivery order needs an agent. Choose the single best AVAILABLE agent to take it.

                ORDER
                - id: %s
                - description: %s

                AVAILABLE AGENTS (choose exactly one, by id)
                %s

                Prefer the agent with the most spare capacity (fewest active orders) unless the
                order description suggests a better fit. This is a normal first assignment.

                %s
                """.formatted(order.getId(), order.getDescription(),
                roster(context.availableAgents()), OUTPUT_CONTRACT);
    }

    public String replanPrompt(RoutingContext context) {
        Order order = context.order();
        RoutingContext.Recovery recovery = context.recovery();
        Agent offline = recovery.unavailableAgent();
        return """
                You are a dispatch routing assistant for ZipRun, a city delivery fleet.

                RECOVERY SITUATION — ACT FAST
                Agent %s (%s) has just gone OFFLINE mid-shift. %d order(s) that were assigned to
                them are now STRANDED and must be reassigned. Their previous assignments are void.

                You are reassigning ONE of those stranded orders now:

                ORDER
                - id: %s
                - description: %s
                - was assigned to the now-offline agent %s

                AVAILABLE AGENTS (choose exactly one, by id)
                %s

                This is a recovery, not a fresh assignment: prioritise getting this order moving
                again quickly with an agent that has spare capacity to absorb extra load right now.

                %s
                """.formatted(
                offline.getId(), offline.getName(), recovery.strandedOrderCount(),
                order.getId(), order.getDescription(), offline.getId(),
                roster(context.availableAgents()), OUTPUT_CONTRACT);
    }

    private String roster(List<Agent> agents) {
        return agents.stream()
                .map(a -> "- %s (%s): %d active order(s)".formatted(a.getId(), a.getName(), a.getActiveOrderCount()))
                .collect(Collectors.joining("\n"));
    }
}
