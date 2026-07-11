package com.travelassistant.trip;

import com.travelassistant.trip.ai.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class TripPlanMapper {
  public TripPlan fromVersion(TripVersion version) {
    List<PlannedDay> days =
        version.getItineraryDays().stream()
            .map(
                d ->
                    new PlannedDay(
                        d.getDayNumber(),
                        d.getDate(),
                        d.getSummary(),
                        d.getActivities().stream()
                            .map(
                                a ->
                                    new PlannedActivity(
                                        a.getSequenceNumber(),
                                        a.getStartTime(),
                                        a.getEndTime(),
                                        a.getTitle(),
                                        a.getLocation(),
                                        a.getDescription(),
                                        a.getEstimatedCost(),
                                        a.getCategory(),
                                        a.getTransportAdvice()))
                            .toList()))
            .toList();
    return new TripPlan(days, List.of());
  }
}
