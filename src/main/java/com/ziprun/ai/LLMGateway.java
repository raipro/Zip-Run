package com.ziprun.ai;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Handles the provider-specific HTTP wire format for Gemini Flash, Groq, and Ollama, and
 * returns the raw text content of the model's response (Addendum B, adapted).
 *
 * <p>What this handles: auth headers, request body shape, response unwrapping, and bounded
 * connect/read timeouts. What remains the strategy's job: prompt construction, JSON parsing,
 * agent-id validation, and fallback (see {@link AiRoutingStrategy}).
 *
 * <p>Throws {@link RuntimeException} on HTTP error or unparseable provider response — the
 * caller is responsible for fallback handling.
 */
@Component
public class LLMGateway implements LlmClient {

    private final LlmProperties props;
    private final RestClient http;

    public LLMGateway(LlmProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        factory.setReadTimeout(props.getReadTimeoutMs());
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String callLLM(String prompt) {
        return switch (props.getProvider().toLowerCase()) {
            case "gemini" -> callGemini(prompt);
            case "groq" -> callOpenAICompatible(prompt, props.getBaseUrl() + "/openai/v1/chat/completions");
            case "ollama" -> callOllama(prompt, props.getBaseUrl() + "/chat");
            default -> throw new IllegalStateException("Unknown LLM provider: " + props.getProvider());
        };
    }

    private String callGemini(String prompt) {
        String url = props.getBaseUrl()
                + "/v1beta/models/" + props.getModel()
                + ":generateContent?key=" + props.getApiKey();
        Map<String, Object> body = Map.of("contents", List.of(
                Map.of("parts", List.of(Map.of("text", prompt)))));

        Map<?, ?> resp = http.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(Map.class);

        try {
            var candidates = (List<?>) resp.get("candidates");
            var content = (Map<?, ?>) ((Map<?, ?>) candidates.get(0)).get("content");
            var parts = (List<?>) content.get("parts");
            return (String) ((Map<?, ?>) parts.get(0)).get("text");
        } catch (Exception e) {
            throw new RuntimeException("Gemini response parse failed", e);
        }
    }

    /**
     * Ollama's native chat API ({@code POST {base}/chat}). {@code stream:false} so we get a
     * single JSON object ({@code {"message":{"content":..}}}) rather than an NDJSON stream.
     */
    private String callOllama(String prompt, String url) {
        Map<String, Object> body = Map.of(
                "model", props.getModel(),
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "stream", false);

        Map<?, ?> resp = http.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(Map.class);

        try {
            var message = (Map<?, ?>) resp.get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            throw new RuntimeException("Ollama response parse failed", e);
        }
    }

    private String callOpenAICompatible(String prompt, String url) {
        Map<String, Object> body = Map.of(
                "model", props.getModel(),
                "messages", List.of(Map.of("role", "user", "content", prompt)));

        Map<?, ?> resp = http.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + props.getApiKey())
                .body(body).retrieve().body(Map.class);

        try {
            var choices = (List<?>) resp.get("choices");
            var message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            throw new RuntimeException("LLM response parse failed", e);
        }
    }
}
