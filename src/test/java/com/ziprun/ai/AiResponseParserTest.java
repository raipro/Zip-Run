package com.ziprun.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiResponseParserTest {

    private final AiResponseParser parser = new AiResponseParser();

    @Test
    void parsesCleanJson() {
        AiRecommendation r = parser.parse("{\"agentId\":\"AGT-002\",\"confidence\":0.82,\"reasoning\":\"least loaded\"}");
        assertThat(r.agentId()).isEqualTo("AGT-002");
        assertThat(r.confidence()).isEqualTo(0.82);
        assertThat(r.reasoning()).isEqualTo("least loaded");
    }

    @Test
    void parsesJsonWrappedInMarkdownFencesAndProse() {
        String raw = "Sure! Here is my pick:\n```json\n{\"agentId\":\"AGT-004\",\"confidence\":0.7,\"reasoning\":\"spare capacity\"}\n```";
        AiRecommendation r = parser.parse(raw);
        assertThat(r.agentId()).isEqualTo("AGT-004");
        assertThat(r.reasoning()).isEqualTo("spare capacity");
    }

    @Test
    void defaultsConfidenceWhenMissing() {
        AiRecommendation r = parser.parse("{\"agentId\":\"AGT-002\",\"reasoning\":\"ok\"}");
        assertThat(r.confidence()).isEqualTo(0.5);
    }

    @Test
    void throwsWhenAgentIdMissing() {
        assertThatThrownBy(() -> parser.parse("{\"confidence\":0.9,\"reasoning\":\"x\"}"))
                .isInstanceOf(AiRoutingException.class)
                .hasMessageContaining("agentId");
    }

    @Test
    void throwsWhenNoJsonPresent() {
        assertThatThrownBy(() -> parser.parse("I'm sorry, I cannot help with that."))
                .isInstanceOf(AiRoutingException.class);
    }

    @Test
    void throwsOnEmptyResponse() {
        assertThatThrownBy(() -> parser.parse("   "))
                .isInstanceOf(AiRoutingException.class);
    }
}
