package com.travelassistant.trip;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_generation_jobs")
public class TripGenerationJob {
  @Id
  @Column(length = 36)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "trip_id")
  private Trip trip;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "base_version_id")
  private TripVersion baseVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "generation_type", nullable = false, length = 16)
  private GenerationType generationType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private GenerationJobStatus status;

  @Column(name = "adjustment_instruction", columnDefinition = "text")
  private String adjustmentInstruction;

  @Column(name = "request_snapshot", nullable = false, columnDefinition = "json")
  private String requestSnapshot;

  @Column(nullable = false)
  private int attempt;

  @Column(name = "failure_code", length = 64)
  private String failureCode;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected TripGenerationJob() {}

  public TripGenerationJob(
      Trip trip, TripVersion base, GenerationType type, String instruction, String snapshot) {
    this.trip = trip;
    baseVersion = base;
    generationType = type;
    adjustmentInstruction = instruction;
    requestSnapshot = snapshot;
    status = GenerationJobStatus.QUEUED;
  }

  @PrePersist
  void init() {
    if (id == null) id = UUID.randomUUID().toString();
    if (createdAt == null) createdAt = Instant.now();
  }

  public void start(Instant now) {
    status = GenerationJobStatus.RUNNING;
    startedAt = now;
    attempt++;
  }

  public void succeed(Instant now) {
    status = GenerationJobStatus.SUCCEEDED;
    completedAt = now;
  }

  public void fail(Instant now, String code) {
    status = GenerationJobStatus.FAILED;
    completedAt = now;
    failureCode = code;
  }

  public String getId() {
    return id;
  }

  public Trip getTrip() {
    return trip;
  }

  public TripVersion getBaseVersion() {
    return baseVersion;
  }

  public GenerationType getGenerationType() {
    return generationType;
  }

  public GenerationJobStatus getStatus() {
    return status;
  }

  public String getAdjustmentInstruction() {
    return adjustmentInstruction;
  }

  public String getRequestSnapshot() {
    return requestSnapshot;
  }
}
