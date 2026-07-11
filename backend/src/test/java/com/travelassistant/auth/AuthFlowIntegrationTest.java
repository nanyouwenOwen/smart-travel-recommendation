package com.travelassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travelassistant.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwords;
  @Autowired JwtEncoder jwtEncoder;

  @Test
  void completesRegistrationProfileRefreshAndLogoutFlow() throws Exception {
    MvcResult registration =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\" Traveler@Example.com \",\"password\":\"secure-pass-123\",\"displayName\":\" 旅行者 \"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andReturn();
    JsonNode registered =
        mapper.readTree(registration.getResponse().getContentAsString()).get("data");
    String access = registered.get("accessToken").asText();
    String refresh = registered.get("refreshToken").asText();

    var user = users.findByEmailIgnoreCase("traveler@example.com").orElseThrow();
    assertThat(user.getEmail()).isEqualTo("traveler@example.com");
    assertThat(user.getPasswordHash()).isNotEqualTo("secure-pass-123");
    assertThat(passwords.matches("secure-pass-123", user.getPasswordHash())).isTrue();

    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.displayName").value("旅行者"));
    mvc.perform(
            patch("/api/v1/users/me")
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"探险家\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.displayName").value("探险家"));

    MvcResult rotated =
        mvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"" + refresh + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String nextRefresh =
        mapper
            .readTree(rotated.getResponse().getContentAsString())
            .get("data")
            .get("refreshToken")
            .asText();
    assertThat(nextRefresh).isNotEqualTo(refresh);

    mvc.perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSED"));
    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + nextRefresh + "\"}"))
        .andExpect(status().isUnauthorized());

    mvc.perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"unknown-token\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void rejectsDuplicateInvalidLoginAndMissingBearerWithStableErrors() throws Exception {
    String body =
        "{\"email\":\"same@example.com\",\"password\":\"secure-pass-123\",\"displayName\":\"User\"}";
    mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"));
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"same@example.com\",\"password\":\"wrong-password\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    mvc.perform(get("/api/v1/users/me").header("X-Request-Id", "auth-test"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.meta.requestId").value("auth-test"));
  }

  @Test
  void rejectsSoftDeletedUsersTamperedTokensAndOversizedUtf8Passwords() throws Exception {
    String body =
        "{\"email\":\"deleted@example.com\",\"password\":\"secure-pass-123\",\"displayName\":\"User\"}";
    MvcResult result =
        mvc.perform(
                post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode data = mapper.readTree(result.getResponse().getContentAsString()).get("data");
    String access = data.get("accessToken").asText();
    String refresh = data.get("refreshToken").asText();
    var user = users.findByEmailIgnoreCase("deleted@example.com").orElseThrow();
    user.softDelete();
    users.saveAndFlush(user);

    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access + "x"))
        .andExpect(status().isUnauthorized());

    String longUtf8 = "旅".repeat(30);
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"bytes@example.com\",\"password\":\""
                        + longUtf8
                        + "\",\"displayName\":\"User\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void serializesConcurrentRefreshAndDetectsReplay() throws Exception {
    MvcResult registration =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"race@example.com\",\"password\":\"secure-pass-123\",\"displayName\":\"Race\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String refresh =
        mapper
            .readTree(registration.getResponse().getContentAsString())
            .get("data")
            .get("refreshToken")
            .asText();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var task =
          (java.util.concurrent.Callable<Integer>)
              () -> {
                ready.countDown();
                start.await();
                return mvc.perform(
                        post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + refresh + "\"}"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              };
      var first = executor.submit(task);
      var second = executor.submit(task);
      ready.await();
      start.countDown();
      assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 401);
    }
  }

  @Test
  void rejectsExpiredAndWrongIssuerAccessTokens() throws Exception {
    String subject = UUID.randomUUID().toString();
    String expired = jwt("smart-travel-assistant-test", subject, Instant.now().minusSeconds(60));
    String wrongIssuer = jwt("another-issuer", subject, Instant.now().plusSeconds(60));
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + wrongIssuer))
        .andExpect(status().isUnauthorized());
  }

  private String jwt(String issuer, String subject, Instant expiry) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(subject)
            .issuedAt(Instant.now().minusSeconds(120))
            .expiresAt(expiry)
            .id(UUID.randomUUID().toString())
            .build();
    return jwtEncoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  @Test
  void allowsOnlyOneConcurrentRegistrationForSameEmail() throws Exception {
    String body =
        "{\"email\":\"parallel@example.com\",\"password\":\"secure-pass-123\",\"displayName\":\"Parallel\"}";
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var task =
          (java.util.concurrent.Callable<Integer>)
              () -> {
                ready.countDown();
                start.await();
                return mvc.perform(
                        post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              };
      var first = executor.submit(task);
      var second = executor.submit(task);
      ready.await();
      start.countDown();
      assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
    }
  }
}
