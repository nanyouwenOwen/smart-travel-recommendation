package com.travelassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelassistant.user.User;
import com.travelassistant.user.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefreshTokenRepositoryTest {
  @Autowired RefreshTokenRepository tokens;
  @Autowired UserRepository users;

  @Test
  void persistsFindsAndRevokesHashedToken() {
    User user = users.saveAndFlush(new User("token-repo@example.com", "hash", "Token"));
    RefreshToken token =
        tokens.saveAndFlush(
            new RefreshToken(
                user, TokenService.hash("secret"), "family", Instant.now().plusSeconds(60)));
    RefreshToken loaded = tokens.findByTokenHash(TokenService.hash("secret")).orElseThrow();
    loaded.revoke(Instant.now(), null);
    tokens.flush();
    assertThat(token.getTokenHash()).doesNotContain("secret");
    assertThat(loaded.getRevokedAt()).isNotNull();
  }

  @Test
  void enforcesUniqueTokenHash() {
    User user = users.saveAndFlush(new User("token-unique@example.com", "hash", "Token"));
    String hash = TokenService.hash("same");
    tokens.saveAndFlush(new RefreshToken(user, hash, "family-a", Instant.now().plusSeconds(60)));
    assertThatThrownBy(
            () ->
                tokens.saveAndFlush(
                    new RefreshToken(user, hash, "family-b", Instant.now().plusSeconds(60))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
