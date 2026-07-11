package com.travelassistant.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TripFlowIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;

  @Test
  void completesCreateAdjustVersionRestoreListAndDeleteFlow() throws Exception {
    String access = register("trip-flow");
    MvcResult accepted =
        mvc.perform(
                post("/api/v1/trips")
                    .header("Authorization", bearer(access))
                    .header("Idempotency-Key", "create-trip-0001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(tripBody("成都", 2).replace("3000.00", "50.00")))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.destination").value("成都"))
            .andReturn();
    String tripId = json(accepted).at("/data/id").asText();

    JsonNode ready = awaitStatus(access, tripId, "READY");
    assertThat(ready.at("/data/currentVersion").asInt()).isEqualTo(1);
    assertThat(ready.at("/data/itinerary").size()).isEqualTo(2);
    assertThat(ready.at("/data/budgetBreakdown/total/amount").asText()).isEqualTo("50.00");

    mvc.perform(
            post("/api/v1/trips")
                .header("Authorization", bearer(access))
                .header("Idempotency-Key", "create-trip-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tripBody("成都", 2).replace("3000.00", "50.00")))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.id").value(tripId));
    mvc.perform(
            post("/api/v1/trips")
                .header("Authorization", bearer(access))
                .header("Idempotency-Key", "create-trip-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tripBody("重庆", 2).replace("3000.00", "50.00")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_CONFLICT"));

    mvc.perform(
            post("/api/v1/trips/{id}/adjustments", tripId)
                .header("Authorization", bearer(access))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instruction\":\"增加当地美食体验\"}"))
        .andExpect(status().isAccepted());
    JsonNode versionTwo = awaitVersion(access, tripId, 2);
    assertThat(versionTwo.at("/data/versionNumber").asInt()).isEqualTo(2);

    mvc.perform(get("/api/v1/trips/{id}/versions", tripId).header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2));
    mvc.perform(
            post("/api/v1/trips/{id}/versions/{version}:restore", tripId, 1)
                .header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.currentVersion").value(1));
    mvc.perform(get("/api/v1/trips?limit=1").header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(tripId))
        .andExpect(jsonPath("$.meta.hasMore").value(false));

    String changed = tripBody("成都和乐山", 3).replace("3000.00", "1.00");
    mvc.perform(
            patch("/api/v1/trips/{id}", tripId)
                .header("Authorization", bearer(access))
                .contentType(MediaType.APPLICATION_JSON)
                .content(changed))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.destination").value("成都和乐山"));
    awaitVersion(access, tripId, 3);
    mvc.perform(
            get("/api/v1/trips/{id}/versions/1", tripId).header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.budgetBreakdown.exceedsBudget").value(false));

    mvc.perform(delete("/api/v1/trips/{id}", tripId).header("Authorization", bearer(access)))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/trips/{id}", tripId).header("Authorization", bearer(access)))
        .andExpect(status().isNotFound());
  }

  @Test
  void hidesTripsFromOtherUsersAndValidatesBoundaries() throws Exception {
    String owner = register("owner");
    String stranger = register("stranger");
    MvcResult result =
        mvc.perform(
                post("/api/v1/trips")
                    .header("Authorization", bearer(owner))
                    .header("Idempotency-Key", "owner-trip-0001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(tripBody("西安", 1)))
            .andExpect(status().isAccepted())
            .andReturn();
    String id = json(result).at("/data/id").asText();
    awaitStatus(owner, id, "READY");
    mvc.perform(get("/api/v1/trips/{id}", id).header("Authorization", bearer(stranger)))
        .andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/v1/trips/{id}", id)
                .header("Authorization", bearer(stranger))
                .contentType(MediaType.APPLICATION_JSON)
                .content(tripBody("西安", 1)))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/v1/trips/{id}", id).header("Authorization", bearer(stranger)))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/trips/{id}/adjustments", id)
                .header("Authorization", bearer(stranger))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instruction\":\"增加美食\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/trips/{id}/versions", id).header("Authorization", bearer(stranger)))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/trips/{id}/versions/1:restore", id)
                .header("Authorization", bearer(stranger)))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/trips")
                .header("Authorization", bearer(owner))
                .header("Idempotency-Key", "short")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tripBody("西安", 31)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/trips")
                .header("Authorization", bearer(owner))
                .header("Idempotency-Key", "invalid-zone-01")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tripBody("西安", 1).replace("Asia/Shanghai", "Mars/Olympus")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    mvc.perform(
            post("/api/v1/trips")
                .header("Authorization", bearer(owner))
                .header("Idempotency-Key", "owner-trip-0002")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tripBody("洛阳", 1)))
        .andExpect(status().isAccepted());
    MvcResult page =
        mvc.perform(get("/api/v1/trips?limit=1").header("Authorization", bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.hasMore").value(true))
            .andReturn();
    String cursor = json(page).at("/meta/nextCursor").asText();
    mvc.perform(
            get("/api/v1/trips")
                .param("cursor", cursor + "x")
                .header("Authorization", bearer(owner)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void concurrentIdenticalIdempotentRequestsReuseOneTrip() throws Exception {
    String access = register("idem-race");
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var task =
          (java.util.concurrent.Callable<MvcResult>)
              () -> {
                ready.countDown();
                start.await();
                return mvc.perform(
                        post("/api/v1/trips")
                            .header("Authorization", bearer(access))
                            .header("Idempotency-Key", "concurrent-create-01")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(tripBody("南京", 1)))
                    .andReturn();
              };
      var first = executor.submit(task);
      var second = executor.submit(task);
      ready.await();
      start.countDown();
      List<MvcResult> results = List.of(first.get(), second.get());
      assertThat(results).allMatch(result -> result.getResponse().getStatus() == 202);
      assertThat(json(results.get(0)).at("/data/id").asText())
          .isEqualTo(json(results.get(1)).at("/data/id").asText());
    }
  }

  private JsonNode awaitStatus(String access, String id, String status) throws Exception {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
    JsonNode node = null;
    while (Instant.now().isBefore(deadline)) {
      MvcResult result =
          mvc.perform(get("/api/v1/trips/{id}", id).header("Authorization", bearer(access)))
              .andExpect(status().isOk())
              .andReturn();
      node = json(result);
      if (status.equals(node.at("/data/status").asText())) return node;
      Thread.sleep(25);
    }
    throw new AssertionError("trip did not reach " + status + ": " + node);
  }

  private JsonNode awaitVersion(String access, String id, int version) throws Exception {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
    while (Instant.now().isBefore(deadline)) {
      MvcResult result =
          mvc.perform(
                  get("/api/v1/trips/{id}/versions/{version}", id, version)
                      .header("Authorization", bearer(access)))
              .andReturn();
      if (result.getResponse().getStatus() == 200) return json(result);
      Thread.sleep(25);
    }
    throw new AssertionError("version " + version + " was not generated");
  }

  private String register(String prefix) throws Exception {
    String email = prefix + "-" + UUID.randomUUID() + "@example.com";
    MvcResult result =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\""
                            + email
                            + "\",\"password\":\"secure-pass-123\",\"displayName\":\"User\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return json(result).at("/data/accessToken").asText();
  }

  private JsonNode json(MvcResult result) throws Exception {
    return mapper.readTree(result.getResponse().getContentAsString());
  }

  private String bearer(String access) {
    return "Bearer " + access;
  }

  private String tripBody(String destination, int days) {
    return "{\"destination\":\""
        + destination
        + "\",\"startDate\":\"2030-06-01\","
        + "\"days\":"
        + days
        + ",\"budget\":{\"amount\":\"3000.00\",\"currency\":\"CNY\"},"
        + "\"travelers\":2,\"preferences\":[\"美食\",\"文化\"],\"timezone\":\"Asia/Shanghai\"}";
  }
}
