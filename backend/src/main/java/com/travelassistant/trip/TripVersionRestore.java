package com.travelassistant.trip;

import com.travelassistant.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_version_restores")
public class TripVersionRestore {
  @Id
  @Column(length = 36)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "trip_id")
  private Trip trip;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "from_version_id")
  private TripVersion fromVersion;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "to_version_id")
  private TripVersion toVersion;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected TripVersionRestore() {}

  public TripVersionRestore(Trip trip, TripVersion from, TripVersion to, User user) {
    this.trip = trip;
    fromVersion = from;
    toVersion = to;
    this.user = user;
  }

  @PrePersist
  void init() {
    if (id == null) id = UUID.randomUUID().toString();
    if (createdAt == null) createdAt = Instant.now();
  }
}
