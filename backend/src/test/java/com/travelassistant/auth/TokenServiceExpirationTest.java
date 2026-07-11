package com.travelassistant.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travelassistant.common.exception.BusinessException;
import com.travelassistant.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class TokenServiceExpirationTest {
  @Test
  void rejectsRefreshTokenAtExpiryBoundary() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    User user = new User("expiry@example.com", "hash", "Expiry");
    RefreshToken expired = new RefreshToken(user, TokenService.hash("raw-token"), "family", now);
    RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    when(repository.findByTokenHash(TokenService.hash("raw-token")))
        .thenReturn(Optional.of(expired));
    AuthProperties properties =
        new AuthProperties(
            "test", Duration.ofMinutes(5), Duration.ofDays(1), "12345678901234567890123456789012");
    TokenService service =
        new TokenService(
            mock(JwtEncoder.class), repository, properties, Clock.fixed(now, ZoneOffset.UTC));

    assertThatThrownBy(() -> service.rotate("raw-token"))
        .isInstanceOf(BusinessException.class)
        .extracting("code")
        .isEqualTo("INVALID_REFRESH_TOKEN");
  }
}
