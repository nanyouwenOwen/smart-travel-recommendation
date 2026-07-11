package com.travelassistant.consultation;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ConversationStreamRepository extends JpaRepository<ConversationStream, String> {
  Optional<ConversationStream> findByIdAndConversationId(String id, String conversationId);

  Optional<ConversationStream> findByAssistantMessageId(String messageId);

  List<ConversationStream> findByStatusIn(Collection<StreamStatus> statuses);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from ConversationStream s where s.id=:id")
  Optional<ConversationStream> lockById(@Param("id") String id);
}
