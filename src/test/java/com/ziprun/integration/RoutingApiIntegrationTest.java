package com.ziprun.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the T-2 routing engine: on-demand suggestions via the rule-based
 * strategy, the one-PENDING-per-order invariant, the accept (reassign) flow, and runtime
 * strategy switching. Runs against the seeded H2 data (AGT-002 / AGT-004 are AVAILABLE).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Transactional
class RoutingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void suggest_runsRuleBasedStrategy_andRecommendsLeastLoadedAvailableAgent() throws Exception {
        // ORD-001 belongs to BUSY AGT-001; least-loaded available agent is AGT-002 (load 0).
        mockMvc.perform(post("/orders/{id}/suggest", "ORD-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORD-001"))
                .andExpect(jsonPath("$.recommendedAgentId").value("AGT-002"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.triggerReason").value("INITIAL"))
                .andExpect(jsonPath("$.strategyUsed").value("rule-based"))
                .andExpect(jsonPath("$.confidence").isNumber())
                .andExpect(jsonPath("$.reasoning").isNotEmpty());

        // The order moved into REASSIGNMENT_PENDING.
        mockMvc.perform(get("/orders").param("status", "REASSIGNMENT_PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='ORD-001')]").isNotEmpty());
    }

    @Test
    void secondSuggestForSameOrder_supersedesTheFirst_onlyOnePendingRemains() throws Exception {
        mockMvc.perform(post("/orders/{id}/suggest", "ORD-001")).andExpect(status().isOk());
        mockMvc.perform(post("/orders/{id}/suggest", "ORD-001")).andExpect(status().isOk());

        // Exactly one PENDING suggestion exists overall (ORD-001 is the only one suggested).
        mockMvc.perform(get("/suggestions").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void acceptSuggestion_reassignsOrder_andRebalancesAgentLoads() throws Exception {
        MvcResult created = mockMvc.perform(post("/orders/{id}/suggest", "ORD-001"))
                .andExpect(status().isOk())
                .andReturn();
        int suggestionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/suggestions/{id}", suggestionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Order is now REASSIGNED to AGT-002.
        mockMvc.perform(get("/orders").param("status", "REASSIGNED"))
                .andExpect(jsonPath("$[?(@.id=='ORD-001')].assignedAgentId").value("AGT-002"));

        // Loads rebalanced: AGT-001 2 -> 1, AGT-002 0 -> 1.
        mockMvc.perform(get("/agents"))
                .andExpect(jsonPath("$[?(@.id=='AGT-001')].activeOrderCount").value(1))
                .andExpect(jsonPath("$[?(@.id=='AGT-002')].activeOrderCount").value(1));
    }

    @Test
    void routingStrategy_isReadableAndRuntimeSwitchValidated() throws Exception {
        mockMvc.perform(get("/config/routing-strategy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value("rule-based"))
                .andExpect(jsonPath("$.available").isArray());

        // Switching to an unregistered strategy is a 409, not a silent no-op.
        mockMvc.perform(patch("/config/routing-strategy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"strategy\":\"ghost\"}"))
                .andExpect(status().isConflict());
    }
}
