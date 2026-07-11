package com.travelassistant.realtime.cache;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface ExternalDataCacheRepository extends JpaRepository<ExternalDataCache, String> {
  Optional<ExternalDataCache> findByCacheKey(String key);

  long deleteByStaleUntilBefore(Instant cutoff);
}
