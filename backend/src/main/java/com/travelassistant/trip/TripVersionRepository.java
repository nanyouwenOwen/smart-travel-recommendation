package com.travelassistant.trip;
import java.util.*; import org.springframework.data.jpa.repository.*;
public interface TripVersionRepository extends JpaRepository<TripVersion,String> {
 List<TripVersion> findByTripIdOrderByVersionNumberDesc(String tripId); Optional<TripVersion> findByTripIdAndVersionNumber(String tripId,int versionNumber);
 long countByTripId(String tripId);
}
