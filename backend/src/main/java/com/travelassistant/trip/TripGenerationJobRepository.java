package com.travelassistant.trip;
import jakarta.persistence.LockModeType; import java.util.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
public interface TripGenerationJobRepository extends JpaRepository<TripGenerationJob,String> {
 boolean existsByTripIdAndStatusIn(String tripId,Collection<GenerationJobStatus> statuses);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select j from TripGenerationJob j join fetch j.trip where j.id=:id") Optional<TripGenerationJob> lockById(@Param("id")String id);
 @Query("select j.id from TripGenerationJob j where (j.status='QUEUED' and j.createdAt<:cutoff) or (j.status='RUNNING' and j.startedAt<:cutoff)") List<String> findStaleIds(@Param("cutoff")java.time.Instant cutoff);
}
