package com.travelassistant.trip;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
  Optional<IdempotencyRecord> findByUserIdAndOperationAndKey(
      String userId, String operation, String key);
}
