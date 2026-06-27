package com.ziprun.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Extracts the JSON recommendation from the LLM's raw text. Tolerant of the common ways
 * models wrap output (markdown fences, leading/trailing prose) by slicing the outermost
 * {@code { ... }}. Throws {@link AiRoutingException} on anything it can't read so the
 * strategy can fall back rather than persist garbage.
 */
@Component
public class AiResponseParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public AiRecommendation parse(String raw) {
        String json = extractJsonObject(raw);
        try {
            JsonNode node = mapper.readTree(json);

            JsonNode idNode = node.get("agentId");
            if (idNode == null || idNode.asText("").isBlank()) {
                throw new AiRoutingException("LLM response missing 'agentId': " + raw);
            }
            String agentId = idNode.asText().trim();
            double confidence = node.path("confidence").asDouble(0.5);
            String reasoning = node.path("reasoning").asText("").trim();

            return new AiRecommendation(agentId, confidence, reasoning);
        } catch (AiRoutingException e) {
            throw e;
        } catch (Exception e) {
            throw new AiRoutingException("Unparseable LLM response: " + raw, e);
        }
    }

    private String extractJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AiRoutingException("Empty LLM response");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AiRoutingException("No JSON object found in LLM response: " + raw);
        }
        return raw.substring(start, end + 1);
    }
}
