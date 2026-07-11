package com.travelassistant.realtime;

import com.travelassistant.common.exception.BusinessException;
import com.travelassistant.realtime.api.RealtimeDtos.*;
import com.travelassistant.realtime.cache.*;
import com.travelassistant.realtime.location.*;
import com.travelassistant.realtime.provider.*;
import com.travelassistant.trip.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class RealtimeService {
  private final LocationDataProvider locations;
  private final WeatherDataProvider weather;
  private final PlaceDataProvider places;
  private final LocationReferenceRepository locationRefs;
  private final ExternalDataCacheRepository cache;
  private final TripRepository trips;
  private final RealtimeProperties properties;
  private final ObjectMapper json;
  private final Clock clock;
  private final Map<String, Deque<Instant>> searchRates =
      new java.util.concurrent.ConcurrentHashMap<>();

  public RealtimeService(
      LocationDataProvider l,
      WeatherDataProvider w,
      PlaceDataProvider p,
      LocationReferenceRepository lr,
      ExternalDataCacheRepository c,
      TripRepository t,
      RealtimeProperties rp,
      ObjectMapper j,
      Clock cl) {
    locations = l;
    weather = w;
    places = p;
    locationRefs = lr;
    cache = c;
    trips = t;
    properties = rp;
    json = j;
    clock = cl;
  }

  @Transactional
  public List<LocationResult> search(String user, String query, String language, int limit) {
    Instant now = clock.instant();
    rate(user, now);
    String normalized = query.trim().toLowerCase(Locale.ROOT);
    String key =
        hash("location|" + normalized + "|" + language.toLowerCase(Locale.ROOT) + "|" + limit);
    CachedResult<LocationResult[]> cached =
        cached(
            key,
            "OPENSTREETMAP_NOMINATIM",
            "LOCATION_SEARCH",
            properties.locationTtl(),
            Duration.ofDays(30),
            LocationResult[].class,
            () ->
                locations.search(query.trim(), language, limit).stream()
                    .limit(limit)
                    .map(
                        c -> {
                          LocationReference ref =
                              locationRefs
                                  .findByProviderAndProviderRef(c.provider(), c.providerRef())
                                  .orElseGet(
                                      () ->
                                          locationRefs.save(
                                              new LocationReference(
                                                  c.provider(),
                                                  c.providerRef(),
                                                  c.name(),
                                                  c.displayName(),
                                                  c.countryCode(),
                                                  c.latitude(),
                                                  c.longitude(),
                                                  c.timezone(),
                                                  c.type(),
                                                  null,
                                                  now,
                                                  now.plus(properties.locationTtl()))));
                          return location(ref, Freshness.FRESH);
                        })
                    .toArray(LocationResult[]::new),
            "LOCATION_DATA_UNAVAILABLE");
    return Arrays.stream(cached.value())
        .map(
            x ->
                cached.stale()
                    ? new LocationResult(
                        x.locationId(),
                        x.name(),
                        x.displayName(),
                        x.countryCode(),
                        x.latitude(),
                        x.longitude(),
                        x.timezone(),
                        x.type(),
                        sources(x.sources(), Freshness.STALE))
                    : x)
        .toList();
  }

  private void rate(String user, Instant now) {
    Deque<Instant> q = searchRates.computeIfAbsent(user, k -> new ArrayDeque<>());
    synchronized (q) {
      while (!q.isEmpty() && q.peekFirst().isBefore(now.minusSeconds(60))) q.removeFirst();
      if (q.size() >= 10)
        throw new BusinessException(
            "REALTIME_RATE_LIMITED", "地点搜索过于频繁", HttpStatus.TOO_MANY_REQUESTS);
      q.addLast(now);
    }
  }

  @Transactional(readOnly = true)
  public LocationReference requireLocation(String id) {
    return locationRefs
        .findById(id)
        .filter(x -> x.getExpiresAt().isAfter(clock.instant()))
        .orElseThrow(
            () ->
                new BusinessException(
                    "LOCATION_REFERENCE_INVALID", "地点引用不存在或已过期", HttpStatus.BAD_REQUEST));
  }

  @Transactional
  public WeatherSnapshot weather(String user, String tripId) {
    Trip t = owned(user, tripId);
    LocationReference l = required(t);
    LocalDate start = t.getStartDate(),
        end = start.plusDays(t.getDays() - 1L),
        today = LocalDate.now(clock),
        availableEnd = today.plusDays(16),
        queryStart = start.isBefore(today) ? today : start,
        queryEnd = end.isAfter(availableEnd) ? availableEnd : end;
    List<String> all = start.datesUntil(end.plusDays(1)).map(LocalDate::toString).toList();
    if (queryStart.isAfter(queryEnd))
      return new WeatherSnapshot(
          l.getTimezone(), List.of(), all, List.of(), Freshness.UNAVAILABLE, "行程日期超出天气预报范围");
    String key = hash("weather|" + l.getId() + "|" + queryStart + "|" + queryEnd);
    CachedResult<WeatherSnapshot> r =
        cached(
            key,
            "OPEN_METEO",
            "WEATHER",
            properties.weatherTtl(),
            properties.weatherStale(),
            WeatherSnapshot.class,
            () -> {
              var value =
                  weather.forecast(
                      l.getLatitude(), l.getLongitude(), l.getTimezone(), queryStart, queryEnd);
              List<String> have = value.days().stream().map(WeatherDay::date).toList();
              List<String> missing = all.stream().filter(d -> !have.contains(d)).toList();
              return new WeatherSnapshot(
                  l.getTimezone(),
                  value.days(),
                  missing,
                  List.of(
                      source(
                          "OPEN_METEO", Freshness.FRESH, clock.instant(), value.sourceUpdatedAt())),
                  Freshness.FRESH,
                  missing.isEmpty() ? null : "部分日期超出天气预报范围");
            },
            "WEATHER_DATA_UNAVAILABLE");
    return freshness(r.value(), r.stale());
  }

  @Transactional
  public PlaceSnapshot places(String user, String tripId, int limit) {
    Trip t = owned(user, tripId);
    LocationReference l = required(t);
    String key = hash("places|" + l.getId() + "|ATTRACTION|" + limit);
    CachedResult<PlaceSnapshot> r =
        cached(
            key,
            "OPENSTREETMAP_OVERPASS",
            "PLACES",
            properties.placesTtl(),
            properties.placesStale(),
            PlaceSnapshot.class,
            () -> {
              var value =
                  places.nearby(
                      l.getLatitude(), l.getLongitude(), properties.placesRadiusMeters(), limit);
              return new PlaceSnapshot(
                  value.places(),
                  List.of(
                      source(
                          "OPENSTREETMAP_OVERPASS",
                          Freshness.FRESH,
                          clock.instant(),
                          value.sourceUpdatedAt())),
                  Freshness.FRESH,
                  "营业时间来自 OpenStreetMap，请向景点官方渠道核验");
            },
            "PLACE_DATA_UNAVAILABLE");
    return freshness(r.value(), r.stale());
  }

  private Trip owned(String user, String id) {
    return trips
        .findByIdAndUserId(id, user)
        .orElseThrow(() -> new BusinessException("TRIP_NOT_FOUND", "行程不存在", HttpStatus.NOT_FOUND));
  }

  private LocationReference required(Trip t) {
    if (t.getDestinationLocation() == null)
      throw new BusinessException("TRIP_LOCATION_REQUIRED", "请先为行程选择明确地点", HttpStatus.CONFLICT);
    return t.getDestinationLocation();
  }

  private synchronized <T> CachedResult<T> cached(
      String key,
      String provider,
      String type,
      Duration ttl,
      Duration stale,
      Class<T> clazz,
      java.util.function.Supplier<T> loader,
      String error) {
    Instant now = clock.instant();
    Optional<ExternalDataCache> found = cache.findByCacheKey(key);
    if (found.isPresent() && found.get().getExpiresAt().isAfter(now))
      return new CachedResult<>(read(found.get(), clazz), false);
    try {
      T loaded = loader.get();
      String payload = json.writeValueAsString(loaded);
      if (payload.length() > 1_000_000) throw new IllegalStateException("payload too large");
      ExternalDataCache row =
          found.orElseGet(
              () ->
                  new ExternalDataCache(
                      key,
                      provider,
                      type,
                      payload,
                      null,
                      now,
                      now.plus(ttl),
                      now.plus(ttl).plus(stale)));
      if (found.isPresent())
        row.refresh(payload, null, now, now.plus(ttl), now.plus(ttl).plus(stale));
      cache.save(row);
      return new CachedResult<>(loaded, false);
    } catch (Exception ex) {
      if (found.isPresent() && found.get().getStaleUntil().isAfter(now)) {
        found.get().fail(error);
        return new CachedResult<>(read(found.get(), clazz), true);
      }
      throw new BusinessException(error, "实时数据暂不可用", HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  private <T> T read(ExternalDataCache row, Class<T> type) {
    try {
      String raw = row.getPayload();
      var tree = json.readTree(raw);
      if (tree.isString()) raw = tree.asText();
      return json.readValue(raw, type);
    } catch (Exception e) {
      cache.delete(row);
      throw new BusinessException(
          "REALTIME_CACHE_INVALID", "实时数据缓存无效", HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  private WeatherSnapshot freshness(WeatherSnapshot v, boolean stale) {
    if (!stale) return v;
    return new WeatherSnapshot(
        v.timezone(),
        v.days(),
        v.unavailableDates(),
        sources(v.sources(), Freshness.STALE),
        Freshness.STALE,
        "供应商暂不可用，正在显示缓存数据");
  }

  private PlaceSnapshot freshness(PlaceSnapshot v, boolean stale) {
    if (!stale) return v;
    return new PlaceSnapshot(
        v.places(),
        sources(v.sources(), Freshness.STALE),
        Freshness.STALE,
        "供应商暂不可用，正在显示缓存数据；营业时间请再次核验");
  }

  private List<DataSourceReference> sources(List<DataSourceReference> s, Freshness f) {
    return s.stream()
        .map(
            x ->
                new DataSourceReference(
                    x.provider(),
                    x.label(),
                    x.sourceUrl(),
                    x.license(),
                    x.retrievedAt(),
                    x.sourceUpdatedAt(),
                    f))
        .toList();
  }

  private LocationResult location(LocationReference r, Freshness f) {
    return new LocationResult(
        r.getId(),
        r.getName(),
        r.getDisplayName(),
        r.getCountryCode(),
        r.getLatitude(),
        r.getLongitude(),
        r.getTimezone(),
        r.getType(),
        List.of(
            source(
                r.getProvider(),
                f,
                r.getFetchedAt(),
                r.getSourceUpdatedAt() == null ? null : r.getSourceUpdatedAt().toString())));
  }

  public LocationView view(LocationReference r) {
    return new LocationView(
        r.getId(),
        r.getName(),
        r.getDisplayName(),
        r.getCountryCode(),
        r.getLatitude(),
        r.getLongitude(),
        r.getTimezone(),
        List.of(
            source(
                r.getProvider(),
                Freshness.FRESH,
                r.getFetchedAt(),
                r.getSourceUpdatedAt() == null ? null : r.getSourceUpdatedAt().toString())));
  }

  private DataSourceReference source(String p, Freshness f, Instant retrieved, String updated) {
    if ("stub".equalsIgnoreCase(properties.mode()))
      return new DataSourceReference(
          "DEMO_STUB",
          "演示数据（非实时）",
          "https://github.com/nanyouwenOwen/smart-travel-recommendation",
          "项目演示数据",
          retrieved.toString(),
          null,
          f);
    boolean osm = p.startsWith("OPENSTREETMAP");
    return new DataSourceReference(
        p,
        osm ? "OpenStreetMap" : "Open-Meteo",
        osm ? "https://www.openstreetmap.org/copyright" : "https://open-meteo.com/",
        osm ? "ODbL 1.0" : "CC BY 4.0",
        retrieved.toString(),
        updated,
        f);
  }

  private String hash(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private record CachedResult<T>(T value, boolean stale) {}
}
