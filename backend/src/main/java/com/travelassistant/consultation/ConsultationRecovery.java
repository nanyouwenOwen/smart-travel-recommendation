package com.travelassistant.consultation;

import java.time.Instant;
import java.util.Set;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ConsultationRecovery {
  private final MessageRepository messages;
  private final ConversationStreamRepository streams;
  private final ConversationStreamEventRepository events;

  public ConsultationRecovery(
      MessageRepository m, ConversationStreamRepository s, ConversationStreamEventRepository e) {
    messages = m;
    streams = s;
    events = e;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void recover() {
    for (ConversationStream stream :
        streams.findByStatusIn(Set.of(StreamStatus.ACTIVE, StreamStatus.DISCONNECTED))) {
      stream.getAssistantMessage().fail("SERVICE_RESTARTED");
      stream.fail(StreamStatus.FAILED);
      String payload =
          "{\"streamId\":\""
              + stream.getId()
              + "\",\"code\":\"SERVICE_RESTARTED\",\"message\":\"服务重启，流已终止\",\"retryable\":false,\"final\":true}";
      events.save(
          new ConversationStreamEvent(
              stream,
              stream.nextSequence(),
              StreamEventType.ERROR,
              payload,
              stream.getExpiresAt()));
    }
    messages
        .findByStatusIn(Set.of(MessageStatus.PENDING, MessageStatus.STREAMING))
        .forEach(m -> m.fail("SERVICE_RESTARTED"));
  }

  @Scheduled(fixedDelayString = "${app.consultation.cleanup-interval:PT1M}")
  @Transactional
  public void cleanup() {
    events.deleteByExpiresAtBefore(Instant.now());
  }
}
