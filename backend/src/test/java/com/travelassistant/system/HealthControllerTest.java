package com.travelassistant.system;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
class HealthControllerTest {
  @Autowired private MockMvc mockMvc;

  @Test
  void returnsHealthAndRequestId() throws Exception {
    mockMvc
        .perform(get("/api/v1/health").header("X-Request-Id", "test-request"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-Id", "test-request"))
        .andExpect(jsonPath("$.data.status").value("UP"))
        .andExpect(jsonPath("$.meta.requestId").value("test-request"));
  }
}
