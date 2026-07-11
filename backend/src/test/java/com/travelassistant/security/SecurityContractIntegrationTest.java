package com.travelassistant.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityContractIntegrationTest.TestEndpoints.class)
class SecurityContractIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;

  @Test
  void usesJwtIdentityForOwnershipAndHidesForeignResources() throws Exception {
    var registration =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"owner@example.com\",\"password\":\"secure-pass-123\",\"displayName\":\"Owner\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    var data = mapper.readTree(registration.getResponse().getContentAsString()).get("data");
    String token = data.get("accessToken").asText();
    String userId =
        mapper
            .readTree(
                mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("data")
            .get("id")
            .asText();

    mvc.perform(
            get("/api/v1/test/resources/{ownerId}", userId)
                .queryParam("userId", "attacker-controlled")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ownerId").value(userId));
    mvc.perform(
            get("/api/v1/test/resources/{ownerId}", "another-user")
                .queryParam("userId", userId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void securityErrorsKeepRequestIdHeaderAndBodyInSync() throws Exception {
    mvc.perform(get("/api/v1/users/me").header("X-Request-Id", "unauthorized-id"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("X-Request-Id", "unauthorized-id"))
        .andExpect(jsonPath("$.meta.requestId").value("unauthorized-id"));

    var registration =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"forbidden@example.com\",\"password\":\"secure-pass-123\",\"displayName\":\"User\"}"))
            .andReturn();
    String token =
        mapper
            .readTree(registration.getResponse().getContentAsString())
            .get("data")
            .get("accessToken")
            .asText();
    mvc.perform(
            get("/api/v1/test/admin")
                .header("Authorization", "Bearer " + token)
                .header("X-Request-Id", "forbidden-id"))
        .andExpect(status().isForbidden())
        .andExpect(header().string("X-Request-Id", "forbidden-id"))
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.meta.requestId").value("forbidden-id"));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestEndpoints {
    @Bean
    TestResourceController testResourceController(OwnershipGuard guard) {
      return new TestResourceController(guard);
    }
  }

  @RestController
  @RequestMapping("/api/v1/test")
  static class TestResourceController {
    private final OwnershipGuard guard;

    TestResourceController(OwnershipGuard guard) {
      this.guard = guard;
    }

    @GetMapping("/resources/{ownerId}")
    Map<String, String> resource(@PathVariable String ownerId, Authentication authentication) {
      guard.requireOwner(authentication.getName(), ownerId);
      return Map.of("ownerId", ownerId);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    void admin() {}
  }
}
