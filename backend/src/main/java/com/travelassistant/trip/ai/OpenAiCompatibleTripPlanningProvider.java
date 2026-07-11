package com.travelassistant.trip.ai;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "app.trip-planning.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleTripPlanningProvider implements TripPlanningProvider {
  private final RestClient client;
  private final ObjectMapper mapper;
  private final String model;
  private final Object schema;
  private final String resourceRoot;

  public OpenAiCompatibleTripPlanningProvider(
      ObjectMapper mapper,
      TripPlanningProperties properties,
      @Value("${app.ai.base-url}") String baseUrl,
      @Value("${app.ai.api-key}") String apiKey,
      @Value("${app.ai.model}") String model)
      throws Exception {
    if (apiKey == null || apiKey.isBlank())
      throw new IllegalStateException("OpenAI compatible provider requires AI_API_KEY");
    this.mapper = mapper;
    this.model = model;
    this.resourceRoot = "prompts/" + properties.promptVersion() + "/";
    this.schema =
        mapper.readValue(
            new ClassPathResource(resourceRoot + "schema.json").getInputStream(), Object.class);
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.connectTimeout());
    factory.setReadTimeout(properties.requestTimeout());
    client =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .build();
  }

  public TripPlan generate(TripPlanningRequest request) {
    try {
      String system =
          new ClassPathResource(resourceRoot + "system.txt")
              .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
      Map<String, Object> body =
          Map.of(
              "model",
              model,
              "messages",
              List.of(
                  Map.of("role", "system", "content", system),
                  Map.of("role", "user", "content", mapper.writeValueAsString(request))),
              "response_format",
              Map.of(
                  "type",
                  "json_schema",
                  "json_schema",
                  Map.of("name", "trip_plan", "strict", true, "schema", schema)));
      JsonNode response =
          client.post().uri("/chat/completions").body(body).retrieve().body(JsonNode.class);
      String content = response.path("choices").get(0).path("message").path("content").asText();
      return mapper.readValue(content, TripPlan.class);
    } catch (RestClientResponseException e) {
      int status = e.getStatusCode().value();
      if (status == 429) throw new TripPlanningException("AI_RATE_LIMITED", "AI 服务限流", true);
      if (status >= 500) throw new TripPlanningException("AI_UNAVAILABLE", "AI 服务暂时不可用", true);
      throw new TripPlanningException("AI_REQUEST_REJECTED", "AI 请求被拒绝", false);
    } catch (ResourceAccessException e) {
      if (hasTimeoutCause(e)) throw new TripPlanningException("AI_TIMEOUT", "AI 请求超时", true);
      throw new TripPlanningException("AI_UNAVAILABLE", "AI 连接失败", true);
    } catch (Exception e) {
      if (e instanceof TripPlanningException t) throw t;
      if (e.getClass().getName().contains("jackson"))
        throw new TripPlanningException("AI_OUTPUT_INVALID", "AI 输出格式无效", false);
      throw new TripPlanningException("AI_UNAVAILABLE", "AI 服务调用失败", true);
    }
  }

  private boolean hasTimeoutCause(Throwable error) {
    for (Throwable current = error; current != null; current = current.getCause())
      if (current instanceof java.net.SocketTimeoutException
          || current instanceof java.net.http.HttpTimeoutException) return true;
    return false;
  }

  public String providerName() {
    return "openai-compatible";
  }

  public String modelName() {
    return model;
  }
}
