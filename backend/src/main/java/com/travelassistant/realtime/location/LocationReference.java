package com.travelassistant.realtime.location;

import com.travelassistant.common.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "location_references",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_location_provider_ref",
            columnNames = {"provider", "provider_ref"}))
public class LocationReference extends AuditableEntity {
  @Column(nullable = false, length = 40)
  private String provider;

  @Column(name = "provider_ref", nullable = false, length = 160)
  private String providerRef;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(name = "display_name", nullable = false, length = 500)
  private String displayName;

  @Column(name = "country_code", length = 2)
  private String countryCode;

  @Column(nullable = false, precision = 10, scale = 7)
  private BigDecimal latitude;

  @Column(nullable = false, precision = 10, scale = 7)
  private BigDecimal longitude;

  @Column(nullable = false, length = 64)
  private String timezone;

  @Column(nullable = false, length = 32)
  private String type;

  @Column(name = "source_updated_at")
  private Instant sourceUpdatedAt;

  @Column(name = "fetched_at", nullable = false)
  private Instant fetchedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  protected LocationReference() {}

  public LocationReference(
      String provider,
      String providerRef,
      String name,
      String displayName,
      String countryCode,
      BigDecimal latitude,
      BigDecimal longitude,
      String timezone,
      String type,
      Instant sourceUpdatedAt,
      Instant fetchedAt,
      Instant expiresAt) {
    this.provider = provider;
    this.providerRef = providerRef;
    this.name = name;
    this.displayName = displayName;
    this.countryCode = countryCode;
    this.latitude = latitude;
    this.longitude = longitude;
    this.timezone = timezone;
    this.type = type;
    this.sourceUpdatedAt = sourceUpdatedAt;
    this.fetchedAt = fetchedAt;
    this.expiresAt = expiresAt;
  }

  public String getProvider() {
    return provider;
  }

  public String getProviderRef() {
    return providerRef;
  }

  public String getName() {
    return name;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public BigDecimal getLatitude() {
    return latitude;
  }

  public BigDecimal getLongitude() {
    return longitude;
  }

  public String getTimezone() {
    return timezone;
  }

  public String getType() {
    return type;
  }

  public Instant getSourceUpdatedAt() {
    return sourceUpdatedAt;
  }

  public Instant getFetchedAt() {
    return fetchedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
