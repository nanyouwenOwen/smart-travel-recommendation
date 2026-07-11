package com.travelassistant.trip;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TripGenerationRecovery {
    private final TripGenerationJobRepository jobs;
    private final TripGenerationService service;
    private final Clock clock;
    private final Duration staleAfter;

    public TripGenerationRecovery(TripGenerationJobRepository jobs, TripGenerationService service, Clock clock,
                                  @Value("${app.trip-planning.job-stale-after:PT2M}") Duration staleAfter) {
        this.jobs = jobs;
        this.service = service;
        this.clock = clock;
        this.staleAfter = staleAfter;
    }

    @Scheduled(fixedDelayString = "${app.trip-planning.recovery-interval:PT30S}")
    public void recover() {
        jobs.findStaleIds(clock.instant().minus(staleAfter)).forEach(service::failStale);
    }
}
