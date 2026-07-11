package com.travelassistant.consultation.ai;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import tools.jackson.databind.*;

@Component
@ConditionalOnProperty(name = "app.consultation.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleConsultationProvider implements ConsultationProvider {
  private final RestClient client;
  private final ObjectMapper mapper;
  private final String model;

  public OpenAiCompatibleConsultationProvider(
      ObjectMapper mapper,
      ConsultationProperties props,
      @Value("${app.ai.base-url}") String url,
      @Value("${app.ai.api-key}") String key,
      @Value("${app.ai.model}") String model) {
    if (key == null || key.isBlank())
      throw new IllegalStateException("Consultation provider requires AI_API_KEY");
    this.mapper = mapper;
    this.model = model;
    SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
    f.setConnectTimeout(Duration.ofSeconds(5));
    f.setReadTimeout(props.requestTimeout());
    client =
        RestClient.builder()
            .baseUrl(url)
            .requestFactory(f)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key)
            .build();
  }

  public ConsultationResult answer(ConsultationPrompt prompt) {
    try {
      JsonNode response =
          client
              .post()
              .uri("/chat/completions")
              .body(body(prompt, false))
              .retrieve()
              .body(JsonNode.class);
      String content = content(response);
      return new ConsultationResult(
          content,
          model,
          integer(response, "prompt_tokens"),
          integer(response, "completion_tokens"));
    } catch (RestClientResponseException e) {
      throw classify(e.getStatusCode().value());
    } catch (ResourceAccessException e) {
      throw new ConsultationException("AI_TIMEOUT", "AI 请求超时", true);
    } catch (ConsultationException e) {
      throw e;
    } catch (Exception e) {
      throw new ConsultationException("AI_OUTPUT_INVALID", "AI 输出格式无效", false);
    }
  }

  public ConsultationResult stream(
      ConsultationPrompt prompt, Consumer<String> chunks, CancellationToken token) {
    try {
      return client
          .post()
          .uri("/chat/completions")
          .body(body(prompt, true))
          .exchange(
              (request, response) -> {
                int status = response.getStatusCode().value();
                if (status >= 400) throw classify(status);
                Integer input = null, output = null;
                StringBuilder all = new StringBuilder();
                try (BufferedReader reader =
                    new BufferedReader(
                        new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                  String line;
                  while ((line = reader.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted() || token.isCancelled())
                      throw new ConsultationException(token.reason(), "流已取消", false);
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]")) break;
                    if (data.isEmpty()) continue;
                    JsonNode event = mapper.readTree(data);
                    JsonNode choices = event.path("choices");
                    if (choices.isArray() && !choices.isEmpty()) {
                      String text = choices.get(0).path("delta").path("content").asText("");
                      if (!text.isEmpty()) {
                        all.append(text);
                        chunks.accept(text);
                      }
                    }
                    JsonNode usage = event.path("usage");
                    if (usage.isObject()) {
                      input =
                          usage.path("prompt_tokens").isInt()
                              ? usage.path("prompt_tokens").asInt()
                              : input;
                      output =
                          usage.path("completion_tokens").isInt()
                              ? usage.path("completion_tokens").asInt()
                              : output;
                    }
                  }
                }
                if (all.isEmpty())
                  throw new ConsultationException("AI_OUTPUT_INVALID", "AI 流没有有效内容", false);
                return new ConsultationResult(all.toString(), model, input, output);
              });
    } catch (RestClientResponseException e) {
      throw classify(e.getStatusCode().value());
    } catch (ResourceAccessException e) {
      throw new ConsultationException("AI_TIMEOUT", "AI 流超时", false);
    } catch (ConsultationException e) {
      throw e;
    } catch (Exception e) {
      throw new ConsultationException("AI_OUTPUT_INVALID", "AI 流协议无效", false);
    }
  }

  private Map<String, Object> body(ConsultationPrompt prompt, boolean stream) {
    List<Map<String, String>> messages =
        prompt.messages().stream()
            .map(v -> Map.of("role", v.role(), "content", v.content()))
            .toList();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("messages", messages);
    body.put("stream", stream);
    if (stream) body.put("stream_options", Map.of("include_usage", true));
    return body;
  }

  private String content(JsonNode response) {
    JsonNode choices = response.path("choices");
    if (!choices.isArray() || choices.isEmpty())
      throw new ConsultationException("AI_OUTPUT_INVALID", "AI 输出缺少 choices", false);
    String value = choices.get(0).path("message").path("content").asText("");
    if (value.isBlank()) throw new ConsultationException("AI_OUTPUT_INVALID", "AI 输出为空", false);
    return value;
  }

  private Integer integer(JsonNode response, String field) {
    JsonNode value = response.path("usage").path(field);
    return value.isInt() ? value.asInt() : null;
  }

  private ConsultationException classify(int status) {
    if (status == 429) return new ConsultationException("AI_RATE_LIMITED", "AI 服务限流", true);
    if (status >= 500) return new ConsultationException("AI_UNAVAILABLE", "AI 服务不可用", true);
    return new ConsultationException("AI_REQUEST_REJECTED", "AI 请求被拒绝", false);
  }

  public String providerName() {
    return "openai-compatible";
  }

  public String modelName() {
    return model;
  }
}
