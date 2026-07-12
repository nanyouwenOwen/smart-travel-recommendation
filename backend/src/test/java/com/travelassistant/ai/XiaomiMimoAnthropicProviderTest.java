package com.travelassistant.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.travelassistant.consultation.ai.*;
import com.travelassistant.trip.ai.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class XiaomiMimoAnthropicProviderTest {
  private HttpServer server;
  private final ObjectMapper mapper = new ObjectMapper();
  private final AtomicReference<JsonNode> request = new AtomicReference<>();
  private final AtomicReference<HttpExchange> exchange = new AtomicReference<>();

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/anthropic/v1/messages",
        value -> {
          exchange.set(value);
          JsonNode body = mapper.readTree(value.getRequestBody());
          request.set(body);
          String rawRequest = body.toString();
          for (int status : List.of(400, 408, 429, 500))
            if (rawRequest.contains("ERROR" + status)) {
              respondStatus(
                  value, status, "{\"type\":\"error\",\"error\":{\"message\":\"secret\"}}");
              return;
            }
          if (rawRequest.contains("INVALID_OUTPUT")) {
            respond(value, "application/json", "{\"content\":[{\"type\":\"tool_use\"}]}");
            return;
          }
          if (body.path("stream").asBoolean(false)) {
            if (rawRequest.contains("SSE_ERROR")) {
              respond(value, "text/event-stream", "data: {\"type\":\"error\"}\n\n");
              return;
            }
            if (rawRequest.contains("SSE_EOF")) {
              respond(
                  value,
                  "text/event-stream",
                  "data: {\"type\":\"message_start\",\"message\":{}}\n\n");
              return;
            }
            if (rawRequest.contains("SSE_BAD_ORDER")) {
              respond(
                  value,
                  "text/event-stream",
                  "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"bad\"}}\n\n");
              return;
            }
            respond(
                value,
                "text/event-stream",
                ": keepalive\r\n"
                    + "data: {\"type\":\"message_start\",\r\n"
                    + "data: \"message\":{\"usage\":{\"input_tokens\":7}}}\r\n\r\n"
                    + "data: {\"type\":\"ping\"}\r\n\r\n"
                    + "event: content_block_start\n"
                    + "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n"
                    + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"你好\"}}\n\n"
                    + "data: {\"type\":\"content_block_stop\",\"index\":0}\n\n"
                    + "data: {\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"thinking\"}}\n\n"
                    + "data: {\"type\":\"content_block_stop\",\"index\":1}\n\n"
                    + "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":2}}\n\n"
                    + "data: {\"type\":\"message_stop\"}\n\n");
          } else if (body.has("system") && body.path("system").asText().contains("JSON Schema")) {
            String plan =
                "{\"days\":[{\"dayNumber\":1,\"date\":\"2030-01-01\",\"summary\":\"抵达\",\"activities\":[{\"sequenceNumber\":1,\"startTime\":\"09:00\",\"endTime\":\"10:00\",\"title\":\"早餐\",\"location\":\"上海\",\"description\":\"本地早餐\",\"estimatedCost\":20.00,\"category\":\"FOOD\",\"transportAdvice\":\"步行\"}]}],\"warnings\":[]}";
            respond(
                value,
                "application/json",
                "{\"content\":[{\"type\":\"text\",\"text\":"
                    + mapper.writeValueAsString(plan)
                    + "}],\"usage\":{\"input_tokens\":10,\"output_tokens\":20}}");
          } else {
            respond(
                value,
                "application/json",
                "{\"content\":[{\"type\":\"text\",\"text\":\"建议步行游览\"}],\"usage\":{\"input_tokens\":3,\"output_tokens\":4}}");
          }
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void sendsAnthropicTripRequestAndParsesPlan() throws Exception {
    XiaomiMimoAnthropicTripPlanningProvider provider =
        new XiaomiMimoAnthropicTripPlanningProvider(
            mapper,
            properties(),
            new TripPlanningProperties(
                "xiaomi-mimo-anthropic",
                "trip-planner/v1",
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                1,
                1,
                1));
    TripPlanningRequest tripRequest =
        new TripPlanningRequest(
            "上海",
            LocalDate.of(2030, 1, 1),
            1,
            1,
            new BigDecimal("1000"),
            "CNY",
            "Asia/Shanghai",
            List.of("美食"),
            null,
            null,
            null);
    TripPlan plan =
        new TripPlanningGateway(
                provider,
                new TripPlanningProperties(
                    "xiaomi-mimo-anthropic",
                    "trip-planner/v1",
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(3),
                    1,
                    1,
                    10),
                new TripPlanValidator())
            .generate("user", tripRequest);
    assertThat(plan.days()).hasSize(1);
    new TripPlanValidator().validate(tripRequest, plan);
    assertThat(new BudgetCalculator().calculate(plan, new BigDecimal("1000")).total())
        .isEqualByComparingTo("20.00");
    assertContract(false);
    assertThat(request.get().path("system").asText()).contains("JSON Schema");
    assertThat(request.get().has("response_format")).isFalse();
  }

  @Test
  void supportsNormalAndStreamingConsultation() {
    XiaomiMimoAnthropicConsultationProvider provider =
        new XiaomiMimoAnthropicConsultationProvider(mapper, properties(), consultationProperties());
    ConsultationPrompt prompt =
        new ConsultationPrompt(
            List.of(
                new ConsultationPrompt.PromptMessage("system", "你是旅游助手"),
                new ConsultationPrompt.PromptMessage("user", "怎么游览？")));
    ConsultationResult answer = provider.answer(prompt);
    assertThat(answer.content()).isEqualTo("建议步行游览");
    assertThat(answer.inputTokens()).isEqualTo(3);
    assertContract(false);

    List<String> chunks = new ArrayList<>();
    ConsultationResult streamed = provider.stream(prompt, chunks::add, new CancellationToken());
    assertThat(chunks).containsExactly("你好");
    assertThat(streamed.content()).isEqualTo("你好");
    assertThat(streamed.inputTokens()).isEqualTo(7);
    assertThat(streamed.outputTokens()).isEqualTo(2);
    assertContract(true);
  }

  @Test
  void rejectsUnsafeConfigurationAndMissingKey() {
    assertThatThrownBy(
            () ->
                new XiaomiMimoProperties(
                    "https://token-plan-cn.xiaomimimo.com/anthropic/v1/messages",
                    "x",
                    "mimo-v2.5",
                    "2023-06-01",
                    1))
        .isInstanceOf(IllegalArgumentException.class);
    XiaomiMimoProperties missing =
        new XiaomiMimoProperties(
            "https://token-plan-cn.xiaomimimo.com/anthropic", "", "mimo-v2.5", "2023-06-01", 8192);
    assertThatThrownBy(missing::requireKey)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("XIAOMI_MIMO_API_KEY");
  }

  @Test
  void cancelledStreamDoesNotEmitChunks() {
    XiaomiMimoAnthropicConsultationProvider provider =
        new XiaomiMimoAnthropicConsultationProvider(mapper, properties(), consultationProperties());
    CancellationToken token = new CancellationToken();
    token.cancel("CLIENT_CANCELLED");
    List<String> chunks = new ArrayList<>();
    ConsultationPrompt prompt =
        new ConsultationPrompt(List.of(new ConsultationPrompt.PromptMessage("user", "取消测试")));
    assertThatThrownBy(() -> provider.stream(prompt, chunks::add, token))
        .isInstanceOf(ConsultationException.class)
        .extracting(value -> ((ConsultationException) value).getCode())
        .isEqualTo("CLIENT_CANCELLED");
    assertThat(chunks).isEmpty();
  }

  @Test
  void rejectsInvalidResponsesDuplicateSystemAndClassifiesRateLimit() {
    XiaomiMimoAnthropicConsultationProvider consultation =
        new XiaomiMimoAnthropicConsultationProvider(mapper, properties(), consultationProperties());
    assertThatThrownBy(
            () ->
                consultation.answer(
                    new ConsultationPrompt(
                        List.of(new ConsultationPrompt.PromptMessage("user", "INVALID_OUTPUT")))))
        .isInstanceOf(ConsultationException.class)
        .extracting(value -> ((ConsultationException) value).getCode())
        .isEqualTo("AI_OUTPUT_INVALID");
    assertThatThrownBy(
            () ->
                consultation.answer(
                    new ConsultationPrompt(
                        List.of(
                            new ConsultationPrompt.PromptMessage("system", "a"),
                            new ConsultationPrompt.PromptMessage("system", "b"),
                            new ConsultationPrompt.PromptMessage("user", "q")))))
        .isInstanceOf(ConsultationException.class)
        .extracting(value -> ((ConsultationException) value).getCode())
        .isEqualTo("AI_REQUEST_REJECTED");
    for (int status : List.of(400, 408, 429, 500)) {
      String expected =
          status == 408
              ? "AI_TIMEOUT"
              : status == 429
                  ? "AI_RATE_LIMITED"
                  : status >= 500 ? "AI_UNAVAILABLE" : "AI_REQUEST_REJECTED";
      assertThatThrownBy(
              () ->
                  consultation.answer(
                      new ConsultationPrompt(
                          List.of(new ConsultationPrompt.PromptMessage("user", "ERROR" + status)))))
          .isInstanceOf(ConsultationException.class)
          .satisfies(
              error -> {
                ConsultationException value = (ConsultationException) error;
                assertThat(value.getCode()).isEqualTo(expected);
                assertThat(value.getMessage()).doesNotContain("secret");
              });
    }
  }

  @Test
  void gatewayRejectsInvalidMimoTripOutput() throws Exception {
    XiaomiMimoAnthropicTripPlanningProvider provider =
        new XiaomiMimoAnthropicTripPlanningProvider(
            mapper,
            properties(),
            new TripPlanningProperties(
                "xiaomi-mimo-anthropic",
                "trip-planner/v1",
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                1,
                1,
                10));
    TripPlanningGateway gateway =
        new TripPlanningGateway(
            provider,
            new TripPlanningProperties(
                "xiaomi-mimo-anthropic",
                "trip-planner/v1",
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                1,
                1,
                10),
            new TripPlanValidator());
    TripPlanningRequest invalid =
        new TripPlanningRequest(
            "INVALID_OUTPUT",
            LocalDate.of(2030, 1, 1),
            1,
            1,
            new BigDecimal("100"),
            "CNY",
            "Asia/Shanghai",
            List.of(),
            null,
            null,
            null);
    assertThatThrownBy(() -> gateway.generate("user", invalid))
        .isInstanceOf(TripPlanningException.class)
        .extracting(value -> ((TripPlanningException) value).getCode())
        .isEqualTo("AI_OUTPUT_INVALID");
  }

  @Test
  void rejectsSseErrorEarlyEofAndInvalidOrder() {
    XiaomiMimoAnthropicConsultationProvider provider =
        new XiaomiMimoAnthropicConsultationProvider(mapper, properties(), consultationProperties());
    for (String value : List.of("SSE_ERROR", "SSE_EOF", "SSE_BAD_ORDER")) {
      ConsultationPrompt prompt =
          new ConsultationPrompt(List.of(new ConsultationPrompt.PromptMessage("user", value)));
      assertThatThrownBy(() -> provider.stream(prompt, ignored -> {}, new CancellationToken()))
          .isInstanceOf(ConsultationException.class);
    }
  }

  private XiaomiMimoProperties properties() {
    return new XiaomiMimoProperties(
        "http://localhost:" + server.getAddress().getPort() + "/anthropic",
        "test-key-not-secret",
        "mimo-v2.5",
        "2023-06-01",
        8192);
  }

  private ConsultationProperties consultationProperties() {
    return new ConsultationProperties(
        "xiaomi-mimo-anthropic",
        1,
        1,
        1,
        Duration.ofSeconds(2),
        Duration.ofSeconds(3),
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        1,
        Duration.ofMinutes(1),
        Duration.ofSeconds(1));
  }

  private void assertContract(boolean streaming) {
    assertThat(exchange.get().getRequestMethod()).isEqualTo("POST");
    assertThat(exchange.get().getRequestHeaders().getFirst("x-api-key"))
        .isEqualTo("test-key-not-secret");
    assertThat(exchange.get().getRequestHeaders().getFirst("anthropic-version"))
        .isEqualTo("2023-06-01");
    assertThat(exchange.get().getRequestHeaders().getFirst("Authorization")).isNull();
    assertThat(request.get().path("model").asText()).isEqualTo("mimo-v2.5");
    assertThat(request.get().path("max_tokens").asInt()).isEqualTo(8192);
    assertThat(request.get().path("stream").asBoolean(false)).isEqualTo(streaming);
    assertThat(request.get().has("stream_options")).isFalse();
  }

  private void respond(HttpExchange value, String contentType, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    value.getResponseHeaders().set("Content-Type", contentType);
    value.sendResponseHeaders(200, bytes.length);
    value.getResponseBody().write(bytes);
    value.close();
  }

  private void respondStatus(HttpExchange value, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    value.sendResponseHeaders(status, bytes.length);
    value.getResponseBody().write(bytes);
    value.close();
  }
}
