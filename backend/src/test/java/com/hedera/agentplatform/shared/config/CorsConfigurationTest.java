package com.hedera.agentplatform.shared.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hedera.agentplatform.shared.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A lone CorsConfigurationSource bean is ignored without Spring Security: preflight requests were
 * answered with 403 "Invalid CORS request" and the browser showed "Failed to fetch". This test
 * fails if we ever go back to that.
 */
@WebMvcTest(HealthController.class)
@Import(CorsConfiguration.class)
class CorsConfigurationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void preflight_from_the_dev_frontend_is_allowed() throws Exception {
        mockMvc
                .perform(
                        options("/api/v1/audit")
                                .header("Origin", "http://localhost:5173")
                                .header("Access-Control-Request-Method", "POST")
                                .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void a_simple_get_carries_the_cors_header() throws Exception {
        mockMvc
                .perform(
                        options("/api/v1/health")
                                .header("Origin", "http://127.0.0.1:5173")
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"));
    }
}
