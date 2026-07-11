package com.travelassistant.consultation.api;

import com.travelassistant.consultation.*;
import com.travelassistant.realtime.api.RealtimeDtos.*;
import java.util.List;

public final class ConsultationDtos {
  private ConsultationDtos() {}

  public record CreateConversationRequest(String title, String tripId) {}

  public record SendMessageRequest(String content) {}

  public record Usage(Integer inputTokens, Integer outputTokens) {}

  public record MessageView(
      String id,
      MessageRole role,
      String content,
      MessageStatus status,
      Integer tripVersionNumber,
      String model,
      Usage usage,
      String errorCode,
      String createdAt,
      String completedAt,
      List<DataSourceReference> sources,
      String dataUpdatedAt,
      Freshness freshness) {}

  public record ConversationView(
      String id,
      String title,
      String tripId,
      String createdAt,
      String updatedAt,
      List<MessageView> messages) {}

  public record ConversationSummary(String id, String title, String tripId, String updatedAt) {}

  public record TurnView(MessageView userMessage, MessageView assistantMessage, boolean replayed) {}

  public record PageResult<T>(List<T> items, String nextCursor, boolean hasMore) {}
}
