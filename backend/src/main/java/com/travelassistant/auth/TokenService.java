package com.travelassistant.auth;

import com.travelassistant.common.exception.BusinessException;
import com.travelassistant.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {
  private final JwtEncoder jwtEncoder;
  private final RefreshTokenRepository tokens;
  private final AuthProperties properties;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public TokenService(
      JwtEncoder jwtEncoder,
      RefreshTokenRepository tokens,
      AuthProperties properties,
      Clock clock) {
    this.jwtEncoder = jwtEncoder;
    this.tokens = tokens;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public TokenPair issue(User user) {
    return createPair(user, UUID.randomUUID().toString());
  }

  @Transactional(noRollbackFor = BusinessException.class)
  public TokenPair rotate(String rawToken) {
    Instant now = clock.instant();
    RefreshToken current = tokens.findByTokenHash(hash(rawToken)).orElseThrow(this::invalid);
    if (current.isRevoked()) {
      if (current.getReplacedByTokenId() != null) {
        tokens
            .findByFamilyIdAndRevokedAtIsNull(current.getFamilyId())
            .forEach(token -> token.revoke(now, null));
        throw new BusinessException("REFRESH_TOKEN_REUSED", "刷新令牌已被重复使用", HttpStatus.UNAUTHORIZED);
      }
      throw invalid();
    }
    if (current.isExpired(now) || current.getUser().isDeleted()) throw invalid();
    TokenPair pair = createPair(current.getUser(), current.getFamilyId());
    RefreshToken replacement = tokens.findByTokenHash(hash(pair.refreshToken())).orElseThrow();
    current.revoke(now, replacement.getId());
    return pair;
  }

  @Transactional
  public void revoke(String rawToken) {
    tokens.findByTokenHash(hash(rawToken)).ifPresent(token -> token.revoke(clock.instant(), null));
  }

  private TokenPair createPair(User user, String familyId) {
    Instant now = clock.instant();
    Instant expiry = now.plus(properties.accessTokenTtl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(user.getId())
            .issuedAt(now)
            .expiresAt(expiry)
            .id(UUID.randomUUID().toString())
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String access = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    String refresh = randomToken();
    tokens.saveAndFlush(
        new RefreshToken(user, hash(refresh), familyId, now.plus(properties.refreshTokenTtl())));
    return new TokenPair(access, refresh, properties.accessTokenTtl().toSeconds());
  }

  private String randomToken() {
    byte[] bytes = new byte[48];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String hash(String value) {
    if (value == null || value.isBlank()) return "invalid";
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private BusinessException invalid() {
    return new BusinessException("INVALID_REFRESH_TOKEN", "刷新令牌无效或已过期", HttpStatus.UNAUTHORIZED);
  }
}
