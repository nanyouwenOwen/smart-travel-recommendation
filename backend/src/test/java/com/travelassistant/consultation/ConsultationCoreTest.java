package com.travelassistant.consultation;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import com.travelassistant.consultation.ai.*;
import com.travelassistant.consultation.security.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConsultationCoreTest {
  private ConsultationProperties props(Duration total) {
    return new ConsultationProperties(
        "fake",
        2,
        20,
        1,
        Duration.ofMillis(100),
        total,
        Duration.ofMillis(80),
        Duration.ofMillis(80),
        2,
        Duration.ofMinutes(10),
        Duration.ofSeconds(30));
  }

  @Test
  void redactsPersonalDataAndRejectsSecretsAndUnsafeOutput() {
    PrivacyRedactor p = new PrivacyRedactor();
    assertThat(p.redact("a@b.com 13812345678 11010519491231002X").content())
        .contains("[EMAIL]", "[PHONE]", "[ID_NUMBER]");
    assertThatThrownBy(() -> p.redact("api_key=secret-value-here"))
        .isInstanceOf(UnsafeContentException.class);
    assertThatThrownBy(() -> new OutputSafetyPolicy().check("密钥 sk-abcdefghijklmnop"))
        .isInstanceOf(UnsafeContentException.class);
  }

  @Test
  void gatewayRetriesTransientFailureAndEnforcesStrictDeadline() {
    AtomicInteger calls = new AtomicInteger();
    ConsultationProvider flaky =
        new ConsultationProvider() {
          public ConsultationResult answer(ConsultationPrompt p) {
            if (calls.incrementAndGet() == 1)
              throw new ConsultationException("AI_UNAVAILABLE", "x", true);
            return new ConsultationResult("ok", "fake", 1, 1);
          }

          public ConsultationResult stream(
              ConsultationPrompt p, java.util.function.Consumer<String> c, CancellationToken t) {
            while (!t.isCancelled()) {
              try {
                Thread.sleep(10);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
              }
            }
            throw new ConsultationException(t.reason(), "timeout", false);
          }

          public String providerName() {
            return "fake";
          }

          public String modelName() {
            return "fake";
          }
        };
    ConsultationGateway gateway = new ConsultationGateway(flaky, props(Duration.ofMillis(120)));
    ConsultationPrompt prompt =
        new ConsultationPrompt(List.of(new ConsultationPrompt.PromptMessage("user", "hi")));
    assertThat(gateway.answer("u", prompt).content()).isEqualTo("ok");
    Instant start = Instant.now();
    assertThatThrownBy(() -> gateway.stream("v", prompt, x -> {}, new CancellationToken()))
        .isInstanceOf(ConsultationException.class)
        .extracting(e -> ((ConsultationException) e).getCode())
        .isEqualTo("AI_TIMEOUT");
    assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofMillis(500));
  }

  @Test
  void firstByteTimeoutInterruptsBlockingProviderAndReleasesPermit() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch interrupted = new CountDownLatch(1);
    ConsultationProvider blocking =
        new ConsultationProvider() {
          public ConsultationResult answer(ConsultationPrompt p) {
            return new ConsultationResult("ok", "fake", 1, 1);
          }

          public ConsultationResult stream(
              ConsultationPrompt p, java.util.function.Consumer<String> c, CancellationToken t) {
            if (calls.incrementAndGet() > 1) return new ConsultationResult("ok", "fake", 1, 1);
            try {
              Thread.sleep(10_000);
            } catch (InterruptedException e) {
              interrupted.countDown();
              Thread.currentThread().interrupt();
            }
            throw new ConsultationException("AI_TIMEOUT", "interrupted", false);
          }

          public String providerName() {
            return "fake";
          }

          public String modelName() {
            return "fake";
          }
        };
    ConsultationGateway gateway = new ConsultationGateway(blocking, props(Duration.ofSeconds(2)));
    ConsultationPrompt prompt =
        new ConsultationPrompt(List.of(new ConsultationPrompt.PromptMessage("user", "hi")));
    Instant start = Instant.now();
    assertThatThrownBy(() -> gateway.stream("same-user", prompt, x -> {}, new CancellationToken()))
        .isInstanceOf(ConsultationException.class)
        .extracting(e -> ((ConsultationException) e).getCode())
        .isEqualTo("AI_TIMEOUT");
    assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofMillis(500));
    assertThat(interrupted.await(500, TimeUnit.MILLISECONDS)).isTrue();
    assertThat(gateway.stream("same-user", prompt, x -> {}, new CancellationToken()).content())
        .isEqualTo("ok");
  }

  @Test
  void openAiAdapterParsesRealSseProtocolAndRequestsStreaming() throws Exception {
    AtomicReference<String> request = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/chat/completions",
        exchange -> {
          request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] body =
              ("data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"好\"}}],\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":2}}\n\ndata: [DONE]\n\n")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      ConsultationProperties p =
          new ConsultationProperties(
              "openai-compatible",
              2,
              20,
              1,
              Duration.ofSeconds(2),
              Duration.ofSeconds(3),
              Duration.ofSeconds(1),
              Duration.ofSeconds(1),
              1,
              Duration.ofMinutes(10),
              Duration.ofSeconds(30));
      OpenAiCompatibleConsultationProvider provider =
          new OpenAiCompatibleConsultationProvider(
              new ObjectMapper(),
              p,
              "http://localhost:" + server.getAddress().getPort(),
              "test-key",
              "test-model");
      List<String> chunks = new ArrayList<>();
      ConsultationResult result =
          provider.stream(
              new ConsultationPrompt(List.of(new ConsultationPrompt.PromptMessage("user", "hi"))),
              chunks::add,
              new CancellationToken());
      assertThat(result.content()).isEqualTo("你好");
      assertThat(chunks).containsExactly("你", "好");
      assertThat(request.get()).contains("\"stream\":true", "\"include_usage\":true");
    } finally {
      server.stop(0);
    }
  }
}
