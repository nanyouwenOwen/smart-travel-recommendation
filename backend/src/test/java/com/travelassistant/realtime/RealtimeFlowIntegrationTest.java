package com.travelassistant.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.*;
import com.travelassistant.realtime.cache.ExternalDataCacheRepository;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class RealtimeFlowIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired ExternalDataCacheRepository caches;
 @Test void requiresAuthenticationBindsOpaqueLocationAndProtectsTripOwnership() throws Exception {
  mvc.perform(get("/api/v1/locations/search").param("q","上海")) .andExpect(status().isUnauthorized());
  String owner=register("rt-owner"),stranger=register("rt-stranger");
  MvcResult found=mvc.perform(get("/api/v1/locations/search").header("Authorization",bearer(owner)).param("q","上海"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].sources[0].provider").value("DEMO_STUB")).andReturn();
  String locationId=node(found).at("/data/0/locationId").asText(); assertThat(locationId).hasSize(36);
  mvc.perform(get("/api/v1/locations/search").header("Authorization",bearer(owner)).param("q","上海")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].locationId").value(locationId));
  String body="{\"destination\":\"上海\",\"destinationLocationId\":\""+locationId+"\",\"startDate\":\""+LocalDate.now().plusDays(1)+"\",\"days\":2,\"budget\":{\"amount\":\"3000.00\",\"currency\":\"CNY\"},\"travelers\":2,\"preferences\":[\"文化\"],\"timezone\":\"Asia/Shanghai\"}";
  MvcResult created=mvc.perform(post("/api/v1/trips").header("Authorization",bearer(owner)).header("Idempotency-Key","realtime-trip-001").contentType(MediaType.APPLICATION_JSON).content(body))
    .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.destinationLocation.id").value(locationId)).andReturn();
  String trip=node(created).at("/data/id").asText();
  mvc.perform(get("/api/v1/trips/{id}/realtime/weather",trip).header("Authorization",bearer(owner)))
    .andExpect(status().isOk()).andExpect(jsonPath("$.data.days.length()").value(2)).andExpect(jsonPath("$.data.freshness").value("FRESH")).andExpect(jsonPath("$.data.sources[0].provider").value("DEMO_STUB"));
  mvc.perform(get("/api/v1/trips/{id}/realtime/places",trip).header("Authorization",bearer(owner)))
    .andExpect(status().isOk()).andExpect(jsonPath("$.data.places[0].openingHours").value("Tu-Su 09:00-17:00")).andExpect(jsonPath("$.data.sources[0].provider").value("DEMO_STUB"));
  MvcResult conversation=mvc.perform(post("/api/v1/conversations").header("Authorization",bearer(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"天气咨询\",\"tripId\":\""+trip+"\"}")).andExpect(status().isCreated()).andReturn();String conversationId=node(conversation).at("/data/id").asText();mvc.perform(post("/api/v1/conversations/{id}/messages",conversationId).header("Authorization",bearer(owner)).header("Idempotency-Key","realtime-answer-001").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"行程天气如何？\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.data.assistantMessage.sources[0].provider").value("DEMO_STUB")).andExpect(jsonPath("$.data.assistantMessage.content").value(org.hamcrest.Matchers.containsString("已结合本次带来源的数据")));
  assertThat(caches.count()).isGreaterThanOrEqualTo(2);
  mvc.perform(get("/api/v1/trips/{id}/realtime/weather",trip).header("Authorization",bearer(stranger))).andExpect(status().isNotFound());
 }
 @Test void oldTripWithoutLocationReturnsExplicitConflictAndInputIsBounded() throws Exception {String token=register("rt-old");String body="{\"destination\":\"同名城市\",\"startDate\":\"2030-06-01\",\"days\":1,\"budget\":{\"amount\":\"100.00\",\"currency\":\"CNY\"},\"travelers\":1,\"preferences\":[\"文化\"],\"timezone\":\"Asia/Shanghai\"}";MvcResult c=mvc.perform(post("/api/v1/trips").header("Authorization",bearer(token)).header("Idempotency-Key","old-trip-0001").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();String id=node(c).at("/data/id").asText();mvc.perform(get("/api/v1/trips/{id}/realtime/weather",id).header("Authorization",bearer(token))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("TRIP_LOCATION_REQUIRED"));mvc.perform(get("/api/v1/locations/search").header("Authorization",bearer(token)).param("q","x")).andExpect(status().isBadRequest());}
 private String register(String p)throws Exception{MvcResult r=mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+p+"-"+UUID.randomUUID()+"@example.com\",\"password\":\"secure-pass-123\",\"displayName\":\"User\"}")).andReturn();return node(r).at("/data/accessToken").asText();} private JsonNode node(MvcResult r)throws Exception{return json.readTree(r.getResponse().getContentAsString());} private String bearer(String t){return "Bearer "+t;}
}
