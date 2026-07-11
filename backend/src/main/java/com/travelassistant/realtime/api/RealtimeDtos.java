package com.travelassistant.realtime.api;

import java.math.BigDecimal;
import java.util.List;

public final class RealtimeDtos {
    private RealtimeDtos() {}
    public enum Freshness { FRESH, STALE, UNAVAILABLE }
    public record DataSourceReference(String provider, String label, String sourceUrl, String license,
                                      String retrievedAt, String sourceUpdatedAt, Freshness freshness) {}
    public record LocationResult(String locationId, String name, String displayName, String countryCode,
                                 BigDecimal latitude, BigDecimal longitude, String timezone, String type,
                                 List<DataSourceReference> sources) {}
    public record LocationView(String id, String name, String displayName, String countryCode,
                               BigDecimal latitude, BigDecimal longitude, String timezone,
                               List<DataSourceReference> sources) {}
    public record WeatherDay(String date, int weatherCode, String condition, BigDecimal temperatureMax,
                             BigDecimal temperatureMin, Integer precipitationProbability,
                             BigDecimal precipitationAmount, BigDecimal windSpeedMax,
                             String sunrise, String sunset) {}
    public record WeatherSnapshot(String timezone, List<WeatherDay> days, List<String> unavailableDates,
                                  List<DataSourceReference> sources, Freshness freshness, String warning) {}
    public record Place(String providerId, String name, String category, BigDecimal latitude,
                        BigDecimal longitude, String openingHours, String website, String providerUrl,
                        String sourceUpdatedAt) {}
    public record PlaceSnapshot(List<Place> places, List<DataSourceReference> sources,
                                Freshness freshness, String warning) {}
}
