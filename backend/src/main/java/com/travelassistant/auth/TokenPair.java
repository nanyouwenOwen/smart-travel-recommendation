package com.travelassistant.auth;

public record TokenPair(String accessToken, String refreshToken, long expiresIn) {
}
