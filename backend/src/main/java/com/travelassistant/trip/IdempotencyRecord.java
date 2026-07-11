package com.travelassistant.trip;

import com.travelassistant.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "idempotency_records",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"user_id", "operation", "idempotency_key"}))
public class IdempotencyRecord {
  @Id
  @Column(length = 36)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(nullable = false, length = 64)
  private String operation;

  @Column(name = "idempotency_key", nullable = false, length = 128)
  private String key;

  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  @Column(name = "resource_id", nullable = false, length = 36)
  private String resourceId;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected IdempotencyRecord() {}

  public IdempotencyRecord(
      User user, String operation, String key, String hash, String resourceId, Instant expires) {
    this.user = user;
    this.operation = operation;
    this.key = key;
    requestHash = hash;
    this.resourceId = resourceId;
    expiresAt = expires;
  }

  @PrePersist
  void init() {
    if (id == null) id = UUID.randomUUID().toString();
    if (createdAt == null) createdAt = Instant.now();
  }

  public String getRequestHash() {
    return requestHash;
  }

  public String getResourceId() {
    return resourceId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
