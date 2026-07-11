package com.travelassistant.trip;

import com.travelassistant.auth.AuthProperties;
import com.travelassistant.common.exception.BusinessException;
import com.travelassistant.trip.api.TripDtos.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripQueryService {
  private final TripRepository trips;
  private final TripVersionRepository versions;
  private final TripResponseMapper mapper;
  private final byte[] cursorSecret;

  public TripQueryService(
      TripRepository t, TripVersionRepository v, TripResponseMapper m, AuthProperties auth) {
    trips = t;
    versions = v;
    mapper = m;
    cursorSecret = auth.jwtSecret().getBytes(StandardCharsets.UTF_8);
  }

  @Transactional(readOnly = true)
  public TripView get(String user, String id) {
    return mapper.trip(owned(user, id));
  }

  @Transactional(readOnly = true)
  public PageResult<TripSummary> list(String user, String cursor, int limit) {
    Pageable page = PageRequest.of(0, limit + 1);
    List<Trip> found;
    if (cursor == null || cursor.isBlank())
      found = trips.findByUserIdOrderByCreatedAtDescIdDesc(user, page).getContent();
    else {
      Cursor c = decode(cursor);
      found = trips.findAfter(user, c.createdAt(), c.id(), page);
    }
    boolean more = found.size() > limit;
    List<Trip> selected = found.subList(0, Math.min(limit, found.size()));
    String next = more ? encode(selected.getLast()) : null;
    return new PageResult<>(selected.stream().map(mapper::summary).toList(), next, more);
  }

  @Transactional(readOnly = true)
  public List<VersionSummary> versions(String user, String id) {
    owned(user, id);
    return versions.findByTripIdOrderByVersionNumberDesc(id).stream()
        .map(mapper::versionSummary)
        .toList();
  }

  @Transactional(readOnly = true)
  public VersionView version(String user, String id, int number) {
    owned(user, id);
    return mapper.version(
        versions.findByTripIdAndVersionNumber(id, number).orElseThrow(this::notFound));
  }

  private Trip owned(String user, String id) {
    return trips.findByIdAndUserId(id, user).orElseThrow(this::notFound);
  }

  private BusinessException notFound() {
    return new BusinessException("TRIP_NOT_FOUND", "行程不存在", HttpStatus.NOT_FOUND);
  }

  private String encode(Trip t) {
    String payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString((t.getCreatedAt() + "|" + t.getId()).getBytes(StandardCharsets.UTF_8));
    return payload + "." + sign(payload);
  }

  private Cursor decode(String value) {
    try {
      String[] token = value.split("\\.", 2);
      if (token.length != 2
          || !MessageDigest.isEqual(
              sign(token[0]).getBytes(StandardCharsets.US_ASCII),
              token[1].getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException();
      String[] p =
          new String(Base64.getUrlDecoder().decode(token[0]), StandardCharsets.UTF_8)
              .split("\\|", 2);
      return new Cursor(Instant.parse(p[0]), p[1]);
    } catch (Exception e) {
      throw new BusinessException("VALIDATION_ERROR", "cursor 无效", HttpStatus.BAD_REQUEST);
    }
  }

  private String sign(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(cursorSecret, "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private record Cursor(Instant createdAt, String id) {}
}
