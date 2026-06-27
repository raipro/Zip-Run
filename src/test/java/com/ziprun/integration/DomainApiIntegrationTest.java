package com.ziprun.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the T-1 domain & API: order creation + listing, agent status
 * updates, and the structured-error contract (validation, not-found, bad enum). Runs
 * against the seeded H2 data (5 agents, 8 orders).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Transactional
class DomainApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void seedDataIsLoaded() throws Exception {
        mockMvc.perform(get("/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8));
    }

    @Test
    void createOrder_returns201_andIncrementsAgentLoad() throws Exception {
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Cake — BTM to HSR\",\"assignedAgentId\":\"AGT-002\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedAgentId").value("AGT-002"))
                .andExpect(jsonPath("$.createdAt").exists());

        // AGT-002 started at load 0 → now 1.
        mockMvc.perform(get("/agents"))
                .andExpect(jsonPath("$[?(@.id=='AGT-002')].activeOrderCount").value(1));
    }

    @Test
    void listOrders_filtersByStatus() throws Exception {
        mockMvc.perform(get("/orders").param("status", "ASSIGNED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8));
        mockMvc.perform(get("/orders").param("status", "DELIVERED"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createOrder_withBlankDescription_returns400() throws Exception {
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"\",\"assignedAgentId\":\"AGT-002\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isNotEmpty());
    }

    @Test
    void createOrder_withUnknownAgent_returns404() throws Exception {
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"x\",\"assignedAgentId\":\"NOPE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void listOrders_withInvalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/orders").param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void patchAgentStatus_updatesAvailability() throws Exception {
        mockMvc.perform(patch("/agents/{id}/status", "AGT-001").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFLINE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("AGT-001"))
                .andExpect(jsonPath("$.status").value("OFFLINE"));
    }

    @Test
    void patchAgentStatus_unknownAgent_returns404() throws Exception {
        mockMvc.perform(patch("/agents/{id}/status", "GHOST").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFLINE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchSuggestion_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/suggestions/{id}", 9999).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isNotFound());
    }
}
