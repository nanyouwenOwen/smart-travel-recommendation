package com.travelassistant.consultation;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRequestRepository extends JpaRepository<MessageRequest, String> {
  Optional<MessageRequest> findByConversationIdAndOperationAndKey(
      String conversation, String operation, String key);
}
