package com.travelassistant.trip.ai;

import com.travelassistant.ai.AnthropicMessagesRequest;
import com.travelassistant.ai.XiaomiMimoClientFactory;
import com.travelassistant.ai.XiaomiMimoProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "app.trip-planning.provider", havingValue = "xiaomi-mimo-anthropic")
public class XiaomiMimoAnthropicTripPlanningProvider implements TripPlanningProvider {
  private final ObjectMapper mapper;
  private final XiaomiMimoProperties properties;
  private final RestClient client;
  private final String resourceRoot;

  public XiaomiMimoAnthropicTripPlanningProvider(
      ObjectMapper mapper, XiaomiMimoProperties properties, TripPlanningProperties tripProperties)
      throws Exception {
    this.mapper = mapper;
    this.properties = properties;
    this.resourceRoot = "prompts/" + tripProperties.promptVersion() + "/";
    this.client =
        XiaomiMimoClientFactory.create(
            properties, tripProperties.connectTimeout(), tripProperties.requestTimeout());
  }

  @Override
  public TripPlan generate(TripPlanningRequest request) {
    try {
      String system =
          new ClassPathResource(resourceRoot + "system.txt")
              .getContentAsString(StandardCharsets.UTF_8);
      String schema =
          new ClassPathResource(resourceRoot + "schema.json")
              .getContentAsString(StandardCharsets.UTF_8);
      String constrainedSystem =
          system + "\n\n只返回一个 JSON 对象，不要输出 Markdown 代码围栏或解释文字。输出必须严格符合以下 JSON Schema：\n" + schema;
      AnthropicMessagesRequest body =
          new AnthropicMessagesRequest(
              properties.model(),
              properties.maxOutputTokens(),
              constrainedSystem,
              List.of(
                  new AnthropicMessagesRequest.Message("user", mapper.writeValueAsString(request))),
              false);
      JsonNode response =
          client.post().uri("/v1/messages").body(body).retrieve().body(JsonNode.class);
      return mapper.readValue(textContent(response), TripPlan.class);
    } catch (RestClientResponseException e) {
      int status = e.getStatusCode().value();
      if (status == 429) throw new TripPlanningException("AI_RATE_LIMITED", "AI 服务限流", true);
      if (status == 408) throw new TripPlanningException("AI_TIMEOUT", "AI 请求超时", true);
      if (status >= 500) throw new TripPlanningException("AI_UNAVAILABLE", "AI 服务暂时不可用", true);
      throw new TripPlanningException("AI_REQUEST_REJECTED", "AI 请求被拒绝", false);
    } catch (ResourceAccessException e) {
      throw new TripPlanningException("AI_TIMEOUT", "AI 请求超时", true);
    } catch (TripPlanningException e) {
      throw e;
    } catch (Exception e) {
      throw new TripPlanningException("AI_OUTPUT_INVALID", "AI 输出格式无效", false);
    }
  }

  private String textContent(JsonNode response) {
    JsonNode blocks = response.path("content");
    if (!blocks.isArray()) invalid();
    StringBuilder text = new StringBuilder();
    for (JsonNode block : blocks) {
      if ("text".equals(block.path("type").asText())) text.append(block.path("text").asText(""));
    }
    String value = text.toString().trim();
    if (value.isEmpty() || !value.startsWith("{") || !value.endsWith("}")) invalid();
    return value;
  }

  private void invalid() {
    throw new TripPlanningException("AI_OUTPUT_INVALID", "AI 输出格式无效", false);
  }

  @Override
  public String providerName() {
    return "xiaomi-mimo-anthropic";
  }

  @Override
  public String modelName() {
    return properties.model();
  }
}
