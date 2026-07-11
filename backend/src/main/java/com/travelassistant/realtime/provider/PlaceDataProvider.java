package com.travelassistant.realtime.provider;

import com.travelassistant.realtime.api.RealtimeDtos.Place;
import java.math.BigDecimal;
import java.util.List;

public interface PlaceDataProvider {
  Result nearby(BigDecimal latitude, BigDecimal longitude, int radius, int limit);

  record Result(List<Place> places, String sourceUpdatedAt) {}
}
