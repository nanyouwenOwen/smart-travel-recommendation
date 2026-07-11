package com.travelassistant.consultation;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, String> {
  List<Message> findByConversationIdAndRoleNotOrderByCreatedAtAscIdAsc(String id, MessageRole role);

  List<Message> findByStatusIn(Collection<MessageStatus> statuses);

  @Query(
      "select m from Message m where m.conversation.id=:conversation and m.role<>'SYSTEM' and (m.createdAt>:time or (m.createdAt=:time and m.id>:id)) order by m.createdAt asc,m.id asc")
  List<Message> findAfter(
      @Param("conversation") String conversation,
      @Param("time") java.time.Instant time,
      @Param("id") String id,
      Pageable page);

  boolean existsByConversationIdAndStatusIn(String id, Collection<MessageStatus> status);
}
