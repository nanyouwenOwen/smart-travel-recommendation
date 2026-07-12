package com.travelassistant.consultation.ai;

import com.travelassistant.ai.AnthropicMessagesRequest;
import com.travelassistant.ai.XiaomiMimoClientFactory;
import com.travelassistant.ai.XiaomiMimoProperties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "app.consultation.provider", havingValue = "xiaomi-mimo-anthropic")
public class XiaomiMimoAnthropicConsultationProvider implements ConsultationProvider {
  private final ObjectMapper mapper;
  private final XiaomiMimoProperties properties;
  private final RestClient client;

  public XiaomiMimoAnthropicConsultationProvider(
      ObjectMapper mapper,
      XiaomiMimoProperties properties,
      ConsultationProperties consultationProperties) {
    this.mapper = mapper;
    this.properties = properties;
    this.client =
        XiaomiMimoClientFactory.create(
            properties, Duration.ofSeconds(5), consultationProperties.requestTimeout());
  }

  @Override
  public ConsultationResult answer(ConsultationPrompt prompt) {
    try {
      JsonNode response =
          client
              .post()
              .uri("/v1/messages")
              .body(body(prompt, false))
              .retrieve()
              .body(JsonNode.class);
      return new ConsultationResult(
          textContent(response),
          properties.model(),
          integer(response.path("usage"), "input_tokens"),
          integer(response.path("usage"), "output_tokens"));
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

  @Override
  public ConsultationResult stream(
      ConsultationPrompt prompt, Consumer<String> chunks, CancellationToken token) {
    try {
      return client
          .post()
          .uri("/v1/messages")
          .body(body(prompt, true))
          .exchange(
              (request, response) -> {
                int status = response.getStatusCode().value();
                if (status >= 400) throw classify(status);
                StringBuilder all = new StringBuilder();
                Integer input = null;
                Integer output = null;
                boolean stopped = false;
                boolean started = false;
                java.util.Set<Integer> textBlocks = new HashSet<>();
                java.util.Set<Integer> openBlocks = new HashSet<>();
                try (BufferedReader reader =
                    new BufferedReader(
                        new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                  String line;
                  StringBuilder eventData = new StringBuilder();
                  while ((line = reader.readLine()) != null) {
                    cancelled(token);
                    if (line.startsWith(":")) continue;
                    if (line.startsWith("data:")) {
                      if (!eventData.isEmpty()) eventData.append('\n');
                      eventData.append(line.substring(5).stripLeading());
                      continue;
                    }
                    if (!line.isEmpty() || eventData.isEmpty()) continue;
                    JsonNode event = mapper.readTree(eventData.toString());
                    eventData.setLength(0);
                    String type = event.path("type").asText();
                    if ("message_start".equals(type)) {
                      if (started) invalidStream();
                      started = true;
                      input = integer(event.path("message").path("usage"), "input_tokens");
                    } else if ("content_block_start".equals(type)) {
                      if (!started || stopped) invalidStream();
                      int index = requiredIndex(event);
                      if (!openBlocks.add(index)) invalidStream();
                      if ("text".equals(event.path("content_block").path("type").asText()))
                        textBlocks.add(index);
                    } else if ("content_block_delta".equals(type)
                        && "text_delta".equals(event.path("delta").path("type").asText())) {
                      int index = requiredIndex(event);
                      if (!started || stopped || !textBlocks.contains(index)) invalidStream();
                      String text = event.path("delta").path("text").asText("");
                      if (!text.isEmpty()) {
                        cancelled(token);
                        all.append(text);
                        chunks.accept(text);
                      }
                    } else if ("content_block_stop".equals(type)) {
                      int index = requiredIndex(event);
                      if (!openBlocks.remove(index)) invalidStream();
                      textBlocks.remove(index);
                    } else if ("message_delta".equals(type)) {
                      if (!started || stopped) invalidStream();
                      output = integer(event.path("usage"), "output_tokens");
                    } else if ("error".equals(type)) {
                      throw new ConsultationException("AI_UNAVAILABLE", "AI 服务不可用", true);
                    } else if ("message_stop".equals(type)) {
                      if (!started || stopped || !openBlocks.isEmpty()) invalidStream();
                      stopped = true;
                      break;
                    }
                  }
                }
                if (!stopped || all.isEmpty())
                  throw new ConsultationException("AI_OUTPUT_INVALID", "AI 流协议无效", false);
                return new ConsultationResult(all.toString(), properties.model(), input, output);
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

  private AnthropicMessagesRequest body(ConsultationPrompt prompt, boolean stream) {
    StringBuilder system = new StringBuilder();
    boolean hasSystem = false;
    List<AnthropicMessagesRequest.Message> messages = new ArrayList<>();
    for (ConsultationPrompt.PromptMessage message : prompt.messages()) {
      if ("system".equals(message.role())) {
        if (hasSystem)
          throw new ConsultationException("AI_REQUEST_REJECTED", "AI system 消息重复", false);
        hasSystem = true;
        system.append(message.content());
      } else if ("user".equals(message.role()) || "assistant".equals(message.role())) {
        messages.add(new AnthropicMessagesRequest.Message(message.role(), message.content()));
      } else {
        throw new ConsultationException("AI_REQUEST_REJECTED", "AI 消息角色无效", false);
      }
    }
    if (messages.isEmpty())
      throw new ConsultationException("AI_REQUEST_REJECTED", "AI 消息为空", false);
    return new AnthropicMessagesRequest(
        properties.model(),
        properties.maxOutputTokens(),
        system.isEmpty() ? null : system.toString(),
        messages,
        stream);
  }

  private int requiredIndex(JsonNode event) {
    if (!event.path("index").isInt()) invalidStream();
    return event.path("index").asInt();
  }

  private void invalidStream() {
    throw new ConsultationException("AI_OUTPUT_INVALID", "AI 流协议无效", false);
  }

  private String textContent(JsonNode response) {
    JsonNode blocks = response.path("content");
    if (!blocks.isArray()) invalid();
    StringBuilder text = new StringBuilder();
    for (JsonNode block : blocks)
      if ("text".equals(block.path("type").asText())) text.append(block.path("text").asText(""));
    if (text.isEmpty()) invalid();
    return text.toString();
  }

  private void invalid() {
    throw new ConsultationException("AI_OUTPUT_INVALID", "AI 输出格式无效", false);
  }

  private Integer integer(JsonNode node, String field) {
    return node.path(field).isInt() ? node.path(field).asInt() : null;
  }

  private void cancelled(CancellationToken token) {
    if (Thread.currentThread().isInterrupted() || token.isCancelled())
      throw new ConsultationException(token.reason(), "流已取消", false);
  }

  private ConsultationException classify(int status) {
    if (status == 429) return new ConsultationException("AI_RATE_LIMITED", "AI 服务限流", true);
    if (status == 408) return new ConsultationException("AI_TIMEOUT", "AI 请求超时", true);
    if (status >= 500) return new ConsultationException("AI_UNAVAILABLE", "AI 服务不可用", true);
    return new ConsultationException("AI_REQUEST_REJECTED", "AI 请求被拒绝", false);
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
