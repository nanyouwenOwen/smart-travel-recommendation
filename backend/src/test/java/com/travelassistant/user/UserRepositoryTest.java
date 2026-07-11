package com.travelassistant.user;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTest {
  @Autowired private UserRepository userRepository;

  @Autowired private EntityManager entityManager;

  @Test
  void auditsAndFiltersSoftDeletedUsers() {
    User user =
        userRepository.saveAndFlush(new User("traveler@example.com", "encoded-password", "旅行者"));

    assertThat(user.getId()).isNotBlank();
    assertThat(user.getCreatedAt()).isNotNull();
    assertThat(user.getUpdatedAt()).isNotNull();
    assertThat(userRepository.findByEmailIgnoreCase("TRAVELER@example.com")).contains(user);

    user.softDelete();
    userRepository.saveAndFlush(user);
    entityManager.clear();

    assertThat(userRepository.findById(user.getId())).isEmpty();
    assertThat(userRepository.findByEmailIgnoreCase("traveler@example.com")).isEmpty();
  }
}
