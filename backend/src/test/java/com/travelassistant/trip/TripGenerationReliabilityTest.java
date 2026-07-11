package com.travelassistant.trip;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.travelassistant.common.exception.BusinessException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class TripGenerationReliabilityTest {
    @Test
    void rejectsBeforePersistenceWhenNoDispatchCapacityRemains() {
        TripDispatchCapacity capacity = new TripDispatchCapacity(0, 0);
        assertThatThrownBy(capacity::reserveForCurrentTransaction)
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("GENERATION_QUEUE_FULL");
    }
    @Test
    void marksJobFailedWhenExecutorRejectsIt() {
        TripGenerationService service = mock(TripGenerationService.class);
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        TripDispatchCapacity capacity = mock(TripDispatchCapacity.class);
        doThrow(new RejectedExecutionException()).when(executor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
        new TripGenerationListener(service, executor, capacity).generate(new TripGenerationRequested("job-1"));
        verify(service).reject("job-1", "GENERATION_QUEUE_FULL");
        verify(capacity).release();
    }

    @Test
    void recoveryFailsStaleQueuedAndRunningJobs() {
        TripGenerationJobRepository jobs = mock(TripGenerationJobRepository.class);
        TripGenerationService service = mock(TripGenerationService.class);
        Clock clock = Clock.fixed(Instant.parse("2030-01-01T00:05:00Z"), ZoneOffset.UTC);
        when(jobs.findStaleIds(Instant.parse("2030-01-01T00:03:00Z")))
                .thenReturn(List.of("queued", "running"));
        new TripGenerationRecovery(jobs, service, clock, Duration.ofMinutes(2)).recover();
        verify(service).failStale("queued");
        verify(service).failStale("running");
    }
}
