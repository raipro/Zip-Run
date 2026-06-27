package com.ziprun.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM provider configuration. Defaults let the app boot without any LLM setup (the AI
 * strategy simply falls back to rule-based until configured). The API key must come from an
 * environment variable — never commit it.
 */
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** gemini | groq | ollama */
    private String provider = "gemini";

    /** API key (not needed for ollama). Inject via env: LLM_API_KEY. */
    private String apiKey = "";

    private String model = "gemini-1.5-flash";

    private String baseUrl = "https://generativelanguage.googleapis.com";

    /** Bounded so a slow/hung LLM never blocks the caller indefinitely. */
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 20000;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
}
