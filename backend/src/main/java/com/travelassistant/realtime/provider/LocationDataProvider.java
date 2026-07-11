package com.travelassistant.realtime.provider;

import java.math.BigDecimal;
import java.util.List;

public interface LocationDataProvider {
  List<Candidate> search(String query, String language, int limit);

  record Candidate(
      String provider,
      String providerRef,
      String name,
      String displayName,
      String countryCode,
      BigDecimal latitude,
      BigDecimal longitude,
      String timezone,
      String type) {}
}
