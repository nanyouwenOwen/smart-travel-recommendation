package com.travelassistant.consultation.ai;

import java.time.Duration;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "app.consultation.provider",
    havingValue = "stub",
    matchIfMissing = true)
public class StubConsultationProvider implements ConsultationProvider {
  private final Duration chunkDelay;

  public StubConsultationProvider(
      @Value("${app.consultation.stub-chunk-delay:PT0S}") Duration chunkDelay) {
    this.chunkDelay = chunkDelay;
  }

  public ConsultationResult answer(ConsultationPrompt p) {
    String question = p.messages().getLast().content();
    boolean realtime = p.messages().stream().anyMatch(m -> m.content().contains("<realtime-data")),
        unavailable = p.messages().stream().anyMatch(m -> m.content().contains("本回答未使用实时数据"));
    String prefix = realtime ? "已结合本次带来源的数据。" : unavailable ? "实时数据暂不可用，本回答未使用实时数据。" : "";
    String answer = prefix + "关于“" + question + "”的建议：请结合预算与行程合理安排，并从官方渠道核验实时价格、天气、营业时间、交通及签证政策。";
    return new ConsultationResult(
        answer, "stub-consultation-v1", p.messages().size() * 10, answer.length());
  }

  public ConsultationResult stream(
      ConsultationPrompt p, Consumer<String> chunks, CancellationToken token) {
    ConsultationResult result = answer(p);
    for (int i = 0; i < result.content().length() && !token.isCancelled(); i += 12) {
      chunks.accept(result.content().substring(i, Math.min(result.content().length(), i + 12)));
      pause(token);
    }
    if (token.isCancelled()) throw new ConsultationException(token.reason(), "cancelled", false);
    return result;
  }

  private void pause(CancellationToken token) {
    if (chunkDelay.isZero() || chunkDelay.isNegative()) return;
    try {
      Thread.sleep(chunkDelay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      token.cancel("CLIENT_CANCELLED");
    }
  }

  public String providerName() {
    return "stub";
  }

  public String modelName() {
    return "stub-consultation-v1";
  }
}
