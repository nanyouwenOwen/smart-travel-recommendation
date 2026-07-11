package com.travelassistant.trip;

import com.travelassistant.common.exception.BusinessException;
import com.travelassistant.trip.ai.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class TripGenerationService {
  private final TripGenerationJobRepository jobs;
  private final TripRepository trips;
  private final TripVersionRepository versions;
  private final TripPlanningGateway gateway;
  private final BudgetCalculator budgets;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final TransactionTemplate tx;

  public TripGenerationService(
      TripGenerationJobRepository j,
      TripRepository t,
      TripVersionRepository v,
      TripPlanningGateway g,
      BudgetCalculator b,
      ObjectMapper m,
      Clock c,
      PlatformTransactionManager manager) {
    jobs = j;
    trips = t;
    versions = v;
    gateway = g;
    budgets = b;
    mapper = m;
    clock = c;
    tx = new TransactionTemplate(manager);
  }

  public void process(String jobId) {
    Work work = tx.execute(status -> claim(jobId));
    if (work == null) return;
    long started = System.nanoTime();
    try {
      TripPlan plan = gateway.generate(work.userId(), work.request());
      BudgetCalculator.BudgetResult budget = budgets.calculate(plan, work.request().budget());
      tx.executeWithoutResult(
          s ->
              publish(
                  work, plan, budget, Duration.ofNanos(System.nanoTime() - started).toMillis()));
    } catch (Exception e) {
      String code =
          e instanceof TripPlanningException p
              ? p.getCode()
              : e instanceof BusinessException b ? b.getCode() : "AI_UNAVAILABLE";
      tx.executeWithoutResult(s -> fail(jobId, work.tripId(), work.userId(), code));
    }
  }

  @Transactional
  public void reject(String jobId, String code) {
    jobs.lockById(jobId)
        .filter(j -> j.getStatus() == GenerationJobStatus.QUEUED)
        .ifPresent(
            j -> {
              j.fail(clock.instant(), code);
              j.getTrip().fail(code);
            });
  }

  @Transactional
  public void failStale(String jobId) {
    jobs.lockById(jobId)
        .filter(
            j ->
                j.getStatus() == GenerationJobStatus.QUEUED
                    || j.getStatus() == GenerationJobStatus.RUNNING)
        .ifPresent(
            j -> {
              j.fail(clock.instant(), "GENERATION_INTERRUPTED");
              j.getTrip().fail("GENERATION_INTERRUPTED");
            });
  }

  private Work claim(String id) {
    TripGenerationJob job = jobs.lockById(id).orElse(null);
    if (job == null || job.getStatus() != GenerationJobStatus.QUEUED) return null;
    job.start(clock.instant());
    try {
      String snapshot = unwrapJsonString(job.getRequestSnapshot());
      TripPlanningRequest request = mapper.readValue(snapshot, TripPlanningRequest.class);
      return new Work(
          id,
          job.getTrip().getId(),
          job.getTrip().getUser().getId(),
          job.getGenerationType(),
          job.getAdjustmentInstruction(),
          request);
    } catch (Exception e) {
      job.fail(clock.instant(), "AI_OUTPUT_INVALID");
      job.getTrip().fail("AI_OUTPUT_INVALID");
      return null;
    }
  }

  private void publish(
      Work work, TripPlan plan, BudgetCalculator.BudgetResult budget, long duration) {
    TripGenerationJob job = jobs.lockById(work.jobId()).orElseThrow();
    if (job.getStatus() != GenerationJobStatus.RUNNING) return;
    Trip trip = trips.lockOwned(work.tripId(), work.userId()).orElse(null);
    if (trip == null) {
      job.fail(clock.instant(), "TRIP_DELETED");
      return;
    }
    int number = Math.toIntExact(versions.countByTripId(trip.getId()) + 1);
    List<String> warningCodes =
        new ArrayList<>(plan.warnings() == null ? List.of() : plan.warnings());
    if (budget.exceedsBudget()) warningCodes.add("BUDGET_EXCEEDED");
    TripVersion version =
        new TripVersion(
            trip,
            number,
            work.type(),
            budget.total(),
            work.request().budget(),
            trip.getCurrency(),
            json(warningCodes),
            gateway.promptVersion(),
            gateway.providerName(),
            gateway.modelName());
    version.setAdjustmentInstruction(work.instruction());
    version.setGenerationDurationMs(duration);
    for (PlannedDay plannedDay : plan.days()) {
      ItineraryDay day =
          new ItineraryDay(
              version, plannedDay.dayNumber(), plannedDay.date(), plannedDay.summary());
      for (PlannedActivity a : plannedDay.activities())
        day.addActivity(
            new Activity(
                day,
                a.sequenceNumber(),
                a.startTime(),
                a.endTime(),
                a.title(),
                a.location(),
                a.description(),
                a.estimatedCost(),
                trip.getCurrency(),
                a.category(),
                a.transportAdvice()));
      version.addDay(day);
    }
    versions.saveAndFlush(version);
    trip.publish(version);
    job.succeed(clock.instant());
  }

  private void fail(String jobId, String tripId, String userId, String code) {
    jobs.lockById(jobId).ifPresent(j -> j.fail(clock.instant(), code));
    trips.lockOwned(tripId, userId).ifPresent(t -> t.fail(code));
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String unwrapJsonString(String value) {
    try {
      String current = value;
      for (int i = 0; i < 3; i++) {
        var node = mapper.readTree(current);
        if (!node.isString()) return current;
        current = node.asText();
      }
      return current;
    } catch (Exception e) {
      return value;
    }
  }

  private record Work(
      String jobId,
      String tripId,
      String userId,
      GenerationType type,
      String instruction,
      TripPlanningRequest request) {}
}
