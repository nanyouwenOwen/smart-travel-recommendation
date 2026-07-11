package com.travelassistant.consultation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConsultationRecoveryTest {
  @Test
  void restartMarksStreamAndMessageAndPersistsTerminalErrorEvent() {
    MessageRepository messages = mock(MessageRepository.class);
    ConversationStreamRepository streams = mock(ConversationStreamRepository.class);
    ConversationStreamEventRepository events = mock(ConversationStreamEventRepository.class);
    ConversationStream stream = mock(ConversationStream.class);
    Message assistant = mock(Message.class);
    when(streams.findByStatusIn(any())).thenReturn(List.of(stream));
    when(stream.getId()).thenReturn("stream-123");
    when(stream.getAssistantMessage()).thenReturn(assistant);
    when(stream.nextSequence()).thenReturn(3);
    when(stream.getExpiresAt()).thenReturn(Instant.now().plusSeconds(60));
    when(messages.findByStatusIn(any())).thenReturn(List.of());
    new ConsultationRecovery(messages, streams, events).recover();
    verify(assistant).fail("SERVICE_RESTARTED");
    verify(stream).fail(StreamStatus.FAILED);
    ArgumentCaptor<ConversationStreamEvent> captor =
        ArgumentCaptor.forClass(ConversationStreamEvent.class);
    verify(events).save(captor.capture());
    assertThat(captor.getValue().getPayload())
        .contains("\"streamId\":\"stream-123\"", "\"code\":\"SERVICE_RESTARTED\"");
  }
}
