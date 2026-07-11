package com.travelassistant.user;

import java.time.Instant;

public record UserProfile(String id, String email, String displayName, Instant createdAt) {
  public static UserProfile from(User user) {
    return new UserProfile(
        user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
  }
}
