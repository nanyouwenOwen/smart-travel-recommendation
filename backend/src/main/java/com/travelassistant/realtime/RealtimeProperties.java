package com.travelassistant.realtime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.realtime")
public record RealtimeProperties(
        @NotBlank String mode,
        @NotBlank String userAgent,
        @NotBlank String nominatimBaseUrl,
        @NotBlank String weatherBaseUrl,
        @NotBlank String overpassBaseUrl,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration locationTtl,
        Duration weatherTtl,
        Duration weatherStale,
        Duration placesTtl,
        Duration placesStale,
        @Min(100) @Max(20000) int placesRadiusMeters) {
    public RealtimeProperties {
        mode = mode == null ? "stub" : mode;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(8) : requestTimeout;
        locationTtl = locationTtl == null ? Duration.ofDays(7) : locationTtl;
        weatherTtl = weatherTtl == null ? Duration.ofMinutes(30) : weatherTtl;
        weatherStale = weatherStale == null ? Duration.ofHours(6) : weatherStale;
        placesTtl = placesTtl == null ? Duration.ofHours(6) : placesTtl;
        placesStale = placesStale == null ? Duration.ofDays(7) : placesStale;
        placesRadiusMeters = placesRadiusMeters == 0 ? 5000 : placesRadiusMeters;
    }
}
